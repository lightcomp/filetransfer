package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.io.InputStream;
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

    private final RecvContext recvCtx;

    private final int seqNum;

    private final boolean last;

    private final Collection<FrameBlock> blocks;

    private final DataHandler dataHandler;

    private final long dataSize;

    private Path dataFile;

    private long dataPos;

    private RecvFrameProcessor(RecvContext recvCtx, int seqNum, boolean last, Collection<FrameBlock> blocks,
            DataHandler dataHandler, long dataSize) {
        this.recvCtx = recvCtx;
        this.seqNum = seqNum;
        this.last = last;
        this.blocks = blocks;
        this.dataHandler = dataHandler;
        this.dataSize = dataSize;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public boolean isLast() {
        return last;
    }

    public void prepareData(Path workDir) {
        Validate.isTrue(dataFile == null);
        // create temporary file
        try {
            dataFile = Files.createTempFile(workDir, Integer.toString(seqNum), null);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to create temporary file for frame data").setCause(e).build();
        }
        // copy frame data to file
        try (InputStream is = dataHandler.getInputStream()) {
            long length = Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
            // copied length must match with specified data size
            if (length != dataSize) {
                deleteData();
                throw TransferExceptionBuilder.from("Frame size does not match data length").addParam("frameSeqNum", seqNum)
                        .addParam("frameSize", dataSize).addParam("dataLength", length).build();
            }
        } catch (IOException e) {
            deleteData();
            throw TransferExceptionBuilder.from("Failed to transfer frame data").addParam("frameSeqNum", seqNum).setCause(e)
                    .build();
        }
    }

    public void process() {
        int blockNum = 1;
        try (ReadableByteChannel dch = openDataChannel()) {
            // set input channel to receive context
            recvCtx.setInputChannel(dch);
            // process all blocks
            for (FrameBlock block : blocks) {
                block.receive(recvCtx);
                blockNum++;
            }
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to process frame block").addParam("frameSeqNum", seqNum)
                    .addParam("blockNum", blockNum).setCause(t).build();
        } finally {
            recvCtx.setInputChannel(null);
            deleteData();
        }
        validate();
    }

    private ReadableByteChannel openDataChannel() {
        ReadableByteChannel dch;
        try {
            dch = Files.newByteChannel(dataFile, StandardOpenOption.READ);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open temporary frame data").addParam("frameSeqNum", seqNum)
                    .addParam("dataFile", dataFile).setCause(e).build();
        }
        return new ReadableByteChannel() {
            @Override
            public boolean isOpen() {
                return dch.isOpen();
            }

            @Override
            public void close() throws IOException {
                dch.close();
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int n = dch.read(dst);
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

    private void deleteData() {
        try {
            Files.delete(dataFile);
        } catch (Throwable t) {
            TransferExceptionBuilder.from("Failed to delete temporary frame data").addParam("frameSeqNum", seqNum).setCause(t)
                    .log(logger);
        }
    }

    public static RecvFrameProcessor create(RecvContext recvCtx, Frame frame) {
        boolean last = Boolean.TRUE.equals(frame.isLast());
        Collection<FrameBlock> blocks = frame.getBlocks().getDesAndFdsAndFes();
        return new RecvFrameProcessor(recvCtx, frame.getSeqNum(), last, blocks, frame.getData(), frame.getDataSize());
    }
}
