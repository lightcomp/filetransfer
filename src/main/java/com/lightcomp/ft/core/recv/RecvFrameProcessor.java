package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

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

    private final long dataSize;

    private final Collection<FrameBlock> blocks;

    private final RecvContext recvCtx;

    private final Path dataFile;

    private ReadableByteChannel channel;

    private long dataPos;

    private RecvFrameProcessor(int seqNum, boolean last, long dataSize, Collection<FrameBlock> blocks, RecvContext recvCtx,
            Path dataFile) {
        this.seqNum = seqNum;
        this.last = last;
        this.dataSize = dataSize;
        this.blocks = blocks;
        this.recvCtx = recvCtx;
        this.dataFile = dataFile;
    }
    
    public int getSeqNum() {
        return seqNum;
    }

    public boolean isLast() {
        return last;
    }

    public void process() {
        int blockNum = 0;
        try {
            prepareChannel();
            for (FrameBlock b : blocks) {
                b.receive(recvCtx);
                blockNum++;
            }
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to process frame block").addParam("frameSeqNum", seqNum)
                    .addParam("blockNum", blockNum).setCause(t).build();
        } finally {
            clearResources();
        }
        validate();
    }

    private void prepareChannel() {
        Validate.isTrue(channel == null);
        try {
            channel = Files.newByteChannel(dataFile, StandardOpenOption.READ);
            recvCtx.setInputChannel(new FrameByteChannelWrapper());
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open frame data").addParam("frameSeqNum", seqNum)
                    .addParam("dataPath", dataFile).setCause(e).build();
        }
    }

    private void validate() {
        if (dataSize != dataPos) {
            throw TransferExceptionBuilder.from("Not all data from stream processed").addParam("frameSeqNum", seqNum)
                    .addParam("dataSize", dataSize).addParam("dataPosition", dataPos).build();
        }
        if (last) {
            Path dirPath = recvCtx.getCurrentDir();
            if (dirPath != null) {
                throw TransferExceptionBuilder.from("Directory inconsistency, after last frame the directory still remains open")
                        .addParam("frameSeqNum", seqNum).addParam("dirPath", dirPath).build();
            }
            Path filePath = recvCtx.getCurrentFile();
            if (filePath != null) {
                throw TransferExceptionBuilder.from("File inconsistency, after last frame the file still remains open")
                        .addParam("frameSeqNum", seqNum).addParam("filePath", filePath).build();
            }
        }
    }

    private void clearResources() {
        recvCtx.setInputChannel(null);
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
            Files.deleteIfExists(dataFile);
        } catch (Throwable t) {
            logger.error("Failed to clear frame resources", t);
        }
    }

    public static RecvFrameProcessor create(Frame frame, RecvContext receiveCtx, Path dataFile) {
        boolean lastFrame = frame.isLast() != null ? frame.isLast() : false;
        Collection<FrameBlock> frameBlocks = frame.getBlocks().getDesAndFdsAndFes();
        return new RecvFrameProcessor(frame.getSeqNum(), lastFrame, frame.getDataSize(), frameBlocks, receiveCtx, dataFile);
    }

    private class FrameByteChannelWrapper implements ReadableByteChannel {

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int n = channel.read(dst);
            dataPos += n;
            return n;
        }
    }
}
