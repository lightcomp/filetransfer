package com.lightcomp.ft.core.send;

import com.lightcomp.ft.xsd.v1.Frame;

public interface SendFrameContext {

    int getSeqNum();

    boolean isLast();

    Frame createFrame();
}
