package com.lightcomp.ft.core.send;

import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public interface SendFrameContext {

    int getSeqNum();

    boolean isLast();
    
    boolean isBlockListFull();

    long getRemainingDataSize();

    void setLast(boolean last);

    void addBlock(FrameBlock block);

    void addBlock(FrameBlock block, FrameBlockStream blockStream);

    Frame createFrame();
}
