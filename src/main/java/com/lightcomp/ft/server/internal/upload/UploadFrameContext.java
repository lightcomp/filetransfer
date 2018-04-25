package com.lightcomp.ft.server.internal.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.core.receiver.ReceiveContext;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class UploadFrameContext implements ReceiveContext {

    public void process(UploadFrameContext frameCtx) throws CanceledException {
        Validate.isTrue(is == null);

        readAllBlocks(frameCtx);

        frameCtx.onProcessed();
    }

    private void readAllBlocks(UploadFrameContext frameCtx) throws CanceledException {
        try {
            is = Files.newInputStream(frameCtx.getDataFilePath(), StandardOpenOption.READ);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open frame data file").addParam("frameSeqNum", frameCtx.getSeqNum())
                    .addParam("path", frameCtx.getDataFilePath()).setCause(e).build();
        }
        int blockNum = 1;
        try {
            for (FrameBlock block : frameCtx.getBlocks()) {
                if (transfer.isCanceled()) {
                    throw new CanceledException();
                }
                try {
                    block.receive(frameCtx);
                } catch (Throwable t) {
                    throw TransferExceptionBuilder.from("Failed to process frame block")
                            .addParam("frameSeqNum", frameCtx.getSeqNum()).addParam("blockNum", blockNum).setCause(t).build();
                }
                blockNum++;
            }
        } finally {
            releaseResources(frameCtx.getDataFilePath());
        }
    }

    private void releaseResources(Path dataFilePath) {
        try {
            if (is != null) {
                is.close();
                is = null;
            }
            Files.delete(dataFilePath);
        } catch (IOException e) {
            logger.warn("Failed to clean up frame resources", e);
        }
    }
    
    public void onProcessed() {

        // validate processed frame
        if (dataSize != dataPos) {
            throw TransferExceptionBuilder.from("Frame blocks doesn't describe data correctly").addParam("frameSeqNum", seqNum)
                    .addParam("dataPos", dataPos).addParam("dataSize", dataSize).build();
        }
        if (last) {
            if (!frameCtx.isCurrentDirRoot()) {
                throw TransferExceptionBuilder.from("Directory inconsistency, after last frame a directory still remains open")
                        .addParam("frameSeqNum", seqNum).addParam("path", frameCtx.getCurrentDir()).build();
            }
            TransferFile file = frameCtx.getCurrentFile();
            if (file != null) {
                throw TransferExceptionBuilder.from("File inconsistency, after last frame a file still remains open")
                        .addParam("frameSeqNum", seqNum).addParam("path", file.getPath()).build();
            }
        }
    }
    
    @Override
    public void openDir(String name) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void closeDir() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void openFile(String name, long size) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void writeFileData(long offset, long size) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void closeFile(long lastModified) {
        // TODO Auto-generated method stub
        
    }
}
