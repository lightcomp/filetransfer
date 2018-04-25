package com.lightcomp.ft.core.sender;

import com.lightcomp.ft.xsd.v1.FrameBlock;

public interface SendFrameContext {

    boolean isBlockListFull();

    long getRemainingDataSize();

    void setLast(boolean last);
    
    void addBlock(FrameBlock block);

    void addBlock(FrameBlock block, FileBlockStream blockStream);
}
