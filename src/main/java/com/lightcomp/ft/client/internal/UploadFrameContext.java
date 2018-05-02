package com.lightcomp.ft.client.internal;

import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.core.send.FileBlockStream;
import com.lightcomp.ft.core.send.FrameDataSource;
import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;
import com.lightcomp.ft.xsd.v1.FrameBlocks;

public class UploadFrameContext implements SendFrameContext {

    private final List<FrameBlock> blocks = new ArrayList<>();

    private final List<FileBlockStream> blockStreams = new ArrayList<>();

    private final int seqNum;

    private final ClientConfig clientConfig;

    private long dataSize;

    private boolean last;

    public UploadFrameContext(int seqNum, ClientConfig clientConfig) {
        this.seqNum = seqNum;
        this.clientConfig = clientConfig;
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
        return clientConfig.getMaxFrameSize() - dataSize;
    }

    @Override
    public boolean isBlockListFull() {
        return blocks.size() >= clientConfig.getMaxFrameBlocks();
    }
    
    @Override
    public void addBlock(FrameBlock block) {
        Validate.isTrue(blocks.size() < clientConfig.getMaxFrameBlocks());
        blocks.add(block);
    }

    @Override
    public void addBlock(FrameBlock block, FileBlockStream blockStream) {
        long newSize = dataSize + blockStream.getSize();
        Validate.isTrue(newSize <= clientConfig.getMaxFrameSize());
        addBlock(block);
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
