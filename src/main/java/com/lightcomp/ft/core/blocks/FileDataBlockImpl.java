package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.xsd.v1.FileData;

public class FileDataBlockImpl extends FileData {

    private static final long serialVersionUID = 1L;

    @Override
    public void receive(RecvContext ctx) {
        ctx.writeFileData(getOff(), getDs());
    }
}
