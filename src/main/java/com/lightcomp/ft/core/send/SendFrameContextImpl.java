package com.lightcomp.ft.core.send;

import java.util.ArrayList;
import java.util.List;


import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.xsd.v1.FrameBlock;
import com.lightcomp.ft.xsd.v1.FrameBlocks;
import com.lightcomp.ft.xsd.v1.Frame;

import jakarta.activation.DataHandler;

public class SendFrameContextImpl implements SendFrameContext {

    private static final Logger logger = LoggerFactory.getLogger(SendFrameContextImpl.class);

    private final List<FrameBlock> blocks = new ArrayList<>();

    private final List<BlockStreamProvider> bsProviders = new ArrayList<>();

    private final int seqNum;

    private final int maxFrameBlocks;

    private final long maxFrameSize;

    private long dataSize;

    private boolean last;

    public SendFrameContextImpl(int seqNum, SendConfig config) {
        this.seqNum = seqNum;
        this.maxFrameBlocks = config.getMaxFrameBlocks();
        this.maxFrameSize = config.getMaxFrameSize();
    }

    @Override
    public int getSeqNum() {
        return seqNum;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public long getRemainingDataSize() {
        return maxFrameSize - dataSize;
    }

    public boolean isBlockListFull() {
        return blocks.size() >= maxFrameBlocks;
    }

    public void addBlock(FrameBlock block) {
        Validate.isTrue(blocks.size() < maxFrameBlocks);
        if (logger.isDebugEnabled()) {
            logger.debug("Adding block: {}", block);
        }
        blocks.add(block);
    }

    public void addBlock(FrameBlock block, BlockStreamProvider bsProvider) {
        long newSize = dataSize + bsProvider.getStreamSize();
        Validate.isTrue(newSize <= maxFrameSize);
        addBlock(block);
        if (logger.isDebugEnabled()) {
            logger.debug("Adding data for block: {}", bsProvider);
        }
        bsProviders.add(bsProvider);
        dataSize = newSize;
    }

    @Override
    public Frame prepareFrame(DataSendFailureCallback failureCallback) {
        // create frame
        Frame frame = new Frame();
        frame.setSeqNum(seqNum);
        frame.setDataSize(dataSize);
        frame.setLast(last);

        // create frame blocks
        FrameBlocks fbs = new FrameBlocks();
        fbs.getDbsAndDesAndFbs().addAll(blocks);
        frame.setBlocks(fbs);

        // set MTOM data source
        FrameInDataSource ds = new FrameInDataSource(bsProviders, failureCallback);
        frame.setData(new DataHandler(ds));

        return frame;
    }
}
