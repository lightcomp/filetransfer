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

import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExBuilder;
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

    /**
     * Receives data from request (MTOM data handler) for future processing.
     */
    public void prepareData(Path workDir) throws TransferException {
        Validate.isTrue(dataFile == null);
        // create temporary file
        try {
            String prefix = "FTRecvFrame-" + Integer.toString(seqNum) + "-";
            dataFile = Files.createTempFile(workDir, prefix, null);
        } catch (IOException e) {
            throw new TransferExBuilder("Failed to create temporary file for frame data").setCause(e).build();
        }
        // copy frame data to file
        try (InputStream is = dataHandler.getInputStream()) {
            long length = Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
            // copied length must match with specified data size
            if (length != dataSize) {
                deleteData();
                throw new TransferExBuilder("Frame size does not match data length").addParam("seqNum", seqNum)
                        .addParam("frameSize", dataSize).addParam("dataLength", length).build();
            }
        } catch (IOException e) {
            deleteData();
            throw new TransferExBuilder("Failed to transfer frame data").addParam("seqNum", seqNum).setCause(e)
                    .build();
        }
    }

    public void process() throws TransferException {
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
            throw new TransferExBuilder("Failed to process frame block").addParam("seqNum", seqNum)
                    .addParam("blockNum", blockNum).setCause(t).build();
        } finally {
            recvCtx.setInputChannel(null);
            deleteData();
        }
        validate();
    }

    private ReadableByteChannel openDataChannel() throws TransferException {
        ReadableByteChannel dch;
        try {
            dch = Files.newByteChannel(dataFile, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new TransferExBuilder("Failed to open temporary frame data").addParam("seqNum", seqNum)
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

    private void validate() throws TransferException {
        if (dataSize != dataPos) {
            throw new TransferExBuilder("Not all data from frame stream processed").addParam("seqNum", seqNum)
                    .addParam("dataSize", dataSize).addParam("dataPosition", dataPos).build();
        }
        if (last) {
            Path currDir = recvCtx.getCurrentDir();
            if (currDir != null) {
                throw new TransferExBuilder(
                        "Directory inconsistency, after last frame the directory still remains open")
                                .addParam("remainingDir", currDir).build();
            }
            Path currFile = recvCtx.getCurrentFile();
            if (currFile != null) {
                throw new TransferExBuilder("File inconsistency, after last frame the file still remains open")
                        .addParam("remainingFile", currFile).build();
            }
        }
    }

    private void deleteData() {
        try {
            Files.delete(dataFile);
        } catch (Throwable t) {
            new TransferExBuilder("Failed to delete temporary frame data").addParam("seqNum", seqNum).setCause(t)
                    .log(logger);
        }
    }

    public static RecvFrameProcessor create(RecvContext recvCtx, Frame frame) {
        boolean last = Boolean.TRUE.equals(frame.isLast());
        Collection<FrameBlock> blocks = frame.getBlocks().getDesAndFdsAndFes();
        return new RecvFrameProcessor(recvCtx, frame.getSeqNum(), last, blocks, frame.getData(), frame.getDataSize());
    }
}
