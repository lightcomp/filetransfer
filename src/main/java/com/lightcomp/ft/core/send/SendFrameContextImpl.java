package com.lightcomp.ft.core.send;

import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;
import com.lightcomp.ft.xsd.v1.FrameBlocks;

public class SendFrameContextImpl implements SendFrameContext {

    private static final Logger logger = LoggerFactory.getLogger(SendFrameContextImpl.class);

    private final List<FrameBlock> blocks = new ArrayList<>();

    private final List<FrameBlockStream> blockStreams = new ArrayList<>();

    private final int seqNum;

    private final int maxFrameBlocks;

    private final long maxFrameSize;

    private long dataSize;

    private boolean last;

    public SendFrameContextImpl(int seqNum, int maxFrameBlocks, long maxFrameSize) {
        this.seqNum = seqNum;
        this.maxFrameBlocks = maxFrameBlocks;
        this.maxFrameSize = maxFrameSize;
    }

    @Override
    public int getSeqNum() {
        return seqNum;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public void setLast(boolean last) {
        this.last = last;
    }

    @Override
    public long getRemainingDataSize() {
        return maxFrameSize - dataSize;
    }

    @Override
    public boolean isBlockListFull() {
        return blocks.size() >= maxFrameBlocks;
    }

    @Override
    public void addBlock(FrameBlock block) {
        Validate.isTrue(blocks.size() < maxFrameBlocks);
        if (logger.isDebugEnabled()) {
            logger.debug("Adding block: {}", block);
        }
        blocks.add(block);
    }

    @Override
    public void addBlock(FrameBlock block, FrameBlockStream blockStream) {
        long newSize = dataSize + blockStream.getSize();
        Validate.isTrue(newSize <= maxFrameSize);
        addBlock(block);
        if (logger.isDebugEnabled()) {
            logger.debug("Adding data for block: {}", blockStream);
        }
        blockStreams.add(blockStream);
        dataSize = newSize;
    }

    @Override
    public Frame createFrame() {
        // create frame
        Frame frame = new Frame();
        frame.setSeqNum(seqNum);
        frame.setDataSize(dataSize);
        frame.setLast(last);

        // create frame blocks
        FrameBlocks fbs = new FrameBlocks();
        fbs.getDesAndFdsAndFes().addAll(blocks);
        frame.setBlocks(fbs);

        // set MTOM data source
        FrameDataSource ds = new FrameDataSource(blockStreams);
        frame.setData(new DataHandler(ds));

        return frame;
    }
}
