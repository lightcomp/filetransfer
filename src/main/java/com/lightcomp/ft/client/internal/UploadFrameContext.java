package com.lightcomp.ft.client.internal;

import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.core.send.FrameBlockStream;
import com.lightcomp.ft.core.send.FrameDataSource;
import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;
import com.lightcomp.ft.xsd.v1.FrameBlocks;

public class UploadFrameContext implements SendFrameContext {

    private static final Logger logger = LoggerFactory.getLogger(UploadFrameContext.class);

    private final List<FrameBlock> blocks = new ArrayList<>();

    private final List<FrameBlockStream> blockStreams = new ArrayList<>();

    private final int seqNum;

    private final ClientConfig config;

    private long dataSize;

    private boolean last;

    public UploadFrameContext(int seqNum, ClientConfig config) {
        this.seqNum = seqNum;
        this.config = config;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public boolean isLast() {
        return last;
    }

    @Override
    public void setLast(boolean last) {
        this.last = last;
    }

    @Override
    public long getRemainingDataSize() {
        return config.getMaxFrameSize() - dataSize;
    }

    @Override
    public boolean isBlockListFull() {
        return blocks.size() >= config.getMaxFrameBlocks();
    }

    @Override
    public void addBlock(FrameBlock block) {
        Validate.isTrue(blocks.size() < config.getMaxFrameBlocks());
        if (logger.isDebugEnabled()) {
            logger.debug("Adding block: {}", block);
        }
        blocks.add(block);
    }

    @Override
    public void addBlock(FrameBlock block, FrameBlockStream blockStream) {
        long newSize = dataSize + blockStream.getSize();
        Validate.isTrue(newSize <= config.getMaxFrameSize());
        addBlock(block);
        if (logger.isDebugEnabled()) {
            logger.debug("Adding data for block: {}", blockStream);
        }
        blockStreams.add(blockStream);
        dataSize = newSize;
    }

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
