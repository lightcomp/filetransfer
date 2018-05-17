package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.xsd.v1.DirBegin;

public class DirBeginBlockImpl extends DirBegin {

    private static final long serialVersionUID = 1L;

    @Override
    public void receive(RecvContext ctx) throws TransferException {
        ctx.openDir(getN());
    }
}
