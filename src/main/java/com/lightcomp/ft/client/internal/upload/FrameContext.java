package com.lightcomp.ft.client.internal.upload;

import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.xsd.v1.FileData;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;
import com.lightcomp.ft.xsd.v1.FrameBlocks;

public class FrameContext {

    private final List<FrameBlock> blocks = new ArrayList<>();

    private final List<FrameBlockData> blockData = new ArrayList<>();

    private final int seqNum;

    private final ClientConfig config;

    private long dataSize;

    private boolean last;

    public FrameContext(int seqNum, ClientConfig config) {
        this.seqNum = seqNum;
        this.config = config;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public long getDataSize() {
        return dataSize;
    }

    public long getRemainingDataSize() {
        return config.getMaxFrameSize() - dataSize;
    }

    public boolean isBlockListFull() {
        return blocks.size() >= config.getMaxFrameBlocks();
    }

    public Frame createFrame() {
        // create frame
        Frame frame = new Frame();
        frame.setSeqNum(seqNum);
        frame.setDataSize(dataSize);
        frame.setLast(last);

        // create frame blocks
        FrameBlocks fbs = new FrameBlocks();
        fbs.getFdsAndFesAndFbs().addAll(blocks);
        frame.setBlocks(fbs);

        // set MTOM data source
        FrameInputDataSource ds = new FrameInputDataSource(blockData);
        frame.setData(new DataHandler(ds));

        return frame;
    }

    public void addBlock(FrameBlock block) {
        Validate.isTrue(blocks.size() < config.getMaxFrameBlocks());
        blocks.add(block);
    }

    public void addDataBlock(FileData block, FrameBlockData data) {
        long newSize = dataSize + block.getSize();
        Validate.isTrue(newSize <= config.getMaxFrameSize());

        addBlock(block);
        blockData.add(data);

        dataSize = newSize;
    }
}
