package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

import javax.activation.DataHandler;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class RecvFrameProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecvFrameProcessor.class);

    private final int seqNum;

    private final boolean last;

    private final Collection<FrameBlock> blocks;

    private final DataHandler data;

    private final long dataSize;

    private final RecvContext recvCtx;

    private Path dataFile;

    private long dataPos;

    private ReadableByteChannel dataChannel;

    private RecvFrameProcessor(int seqNum, boolean last, Collection<FrameBlock> blocks, DataHandler data, long dataSize,
            RecvContext recvCtx) {
        this.seqNum = seqNum;
        this.last = last;
        this.blocks = blocks;
        this.data = data;
        this.dataSize = dataSize;
        this.recvCtx = recvCtx;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public boolean isLast() {
        return last;
    }

    public void transfer(Path workDir) {
        Validate.isTrue(dataFile == null);
        // create temporary data file
        String prefix = Integer.toString(seqNum);
        try {
            dataFile = Files.createTempFile(workDir, prefix, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // copy whole frame data to file
        try (InputStream is = data.getInputStream()) {
            long length = Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
            // copied length must match with specified data size
            if (length != dataSize) {
                clearData();
                throw TransferExceptionBuilder.from("Frame size does not match data length").addParam("frameSeqNum", seqNum)
                        .addParam("frameSize", dataSize).addParam("dataLength", length).build();
            }
        } catch (IOException e) {
            clearData();
            throw TransferExceptionBuilder.from("Failed to transfer frame data").addParam("frameSeqNum", seqNum).setCause(e)
                    .build();
        }
    }

    public void process() {
        int blockNum = 1;
        try (ReadableByteChannel rbch = openDataChannel()) {
            // set input channel for receive context
            recvCtx.setInputChannel(rbch);
            // process all blocks
            for (FrameBlock block : blocks) {
                block.receive(recvCtx);
                blockNum++;
            }
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to process frame block").addParam("frameSeqNum", seqNum)
                    .addParam("blockNum", blockNum).setCause(t).build();
        } finally {
            // release input channel
            recvCtx.setInputChannel(null);
            // close and delete temp data
            clearData();
        }
        validate();
    }

    private ReadableByteChannel openDataChannel() {
        Validate.isTrue(dataChannel == null);
        try {
            dataChannel = Files.newByteChannel(dataFile, StandardOpenOption.READ);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open temporary frame data").addParam("frameSeqNum", seqNum)
                    .addParam("dataFile", dataFile).setCause(e).build();
        }
        return new ReadableByteChannel() {
            @Override
            public boolean isOpen() {
                return dataChannel.isOpen();
            }

            @Override
            public void close() throws IOException {
                dataChannel.close();
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int n = dataChannel.read(dst);
                dataPos += n;
                return n;
            }
        };
    }

    private void validate() {
        if (dataSize != dataPos) {
            throw TransferExceptionBuilder.from("Not all data from stream processed").addParam("frameSeqNum", seqNum)
                    .addParam("dataSize", dataSize).addParam("dataPosition", dataPos).build();
        }
        if (last) {
            Path currDir = recvCtx.getCurrentDir();
            if (currDir != null) {
                throw TransferExceptionBuilder.from("Directory inconsistency, after last frame the directory still remains open")
                        .addParam("frameSeqNum", seqNum).addParam("remainingDir", currDir).build();
            }
            Path currFile = recvCtx.getCurrentFile();
            if (currFile != null) {
                throw TransferExceptionBuilder.from("File inconsistency, after last frame the file still remains open")
                        .addParam("frameSeqNum", seqNum).addParam("remainingFile", currFile).build();
            }
        }
    }

    private void clearData() {
        try {
            if (dataChannel != null) {
                dataChannel.close();
                dataChannel = null;
            }
            Files.delete(dataFile);
        } catch (Throwable t) {
            TransferExceptionBuilder.from("Failed to clear temporary frame data").addParam("frameSeqNum", seqNum).setCause(t)
                    .log(logger);
        }
    }

    public static RecvFrameProcessor create(Frame frame, RecvContext recvCtx) {
        boolean last = Boolean.TRUE.equals(frame.isLast());
        Collection<FrameBlock> blocks = frame.getBlocks().getDesAndFdsAndFes();
        return new RecvFrameProcessor(frame.getSeqNum(), last, blocks, frame.getData(), frame.getDataSize(), recvCtx);
    }
}
