package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.receiver.ReceiveContext;
import com.lightcomp.ft.xsd.v1.FileBegin;

public class FileBeginBlockImpl extends FileBegin {

    private static final long serialVersionUID = 1L;

    @Override
    public void receive(ReceiveContext ctx) {
        ctx.openFile(getN(), getFs());
    }
}
