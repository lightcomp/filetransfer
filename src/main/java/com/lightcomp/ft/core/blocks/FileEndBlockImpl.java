package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.receiver.ReceiveContext;
import com.lightcomp.ft.xsd.v1.FileEnd;

public class FileEndBlockImpl extends FileEnd {

    private static final long serialVersionUID = 1L;

    @Override
    public void receive(ReceiveContext ctx) {
        ctx.closeFile(getLm());
    }
}
