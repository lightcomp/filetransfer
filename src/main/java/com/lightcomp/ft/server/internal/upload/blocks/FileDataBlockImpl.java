package com.lightcomp.ft.server.internal.upload.blocks;

import java.io.InputStream;

import com.lightcomp.ft.server.internal.upload.FrameContext;
import com.lightcomp.ft.xsd.v1.FileData;

public class FileDataBlockImpl extends FileData {

    private static final long serialVersionUID = 1L;

    @Override
    public void saveBlock(InputStream is, FrameContext frameCtx) {
        frameCtx.writeFileData(is, getFrameOffset(), getOffset(), getSize());
    }
}
