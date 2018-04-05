package com.lightcomp.ft.server.impl.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.impl.TransferContext;
import com.lightcomp.ft.xsd.v1.FileChunk;
import com.lightcomp.ft.xsd.v1.Frame;

public class FrameTask implements Task {

    private final Frame frame;

    private final TransferContext transferCtx;

    private long writtenSize;

    public FrameTask(Frame frame, TransferContext transferCtx) {
        this.frame = frame;
        this.transferCtx = transferCtx;
    }

    @Override
    public void run() throws CanceledException {
        List<TransferFileReader> fileChunks = frame.getFileChunks().getList();
        if (fileChunks.isEmpty()) {
            return;
        }
        try (InputStream is = frame.getData().getInputStream()) {
            for (TransferFileReader fc : fileChunks) {
                if (transferCtx.isCancelRequested()) {
                    throw new CanceledException();
                }
                writeFileChunk(fc, is);
            }
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open frame data stream").addParam("frameId", frame.getFrameId())
                    .setCause(e).build();
        }
    }

    private void writeFileChunk(TransferFileReader fc, InputStream is) {
        if (writtenSize != fc.getFrameOffset()) {
            throw TransferExceptionBuilder.from("File frame offset doesn't match with current position of frame data stream")
                    .addParam("frameId", frame.getFrameId()).addParam("fileId", fc.getFileId())
                    .addParam("frameOffset", fc.getFrameOffset()).addParam("streamPosition", writtenSize).build();
        }
        TransferFile tf = transferCtx.getFile(fc.getFileId());
        tf.writeChunk(is, fc.getOffset(), fc.getSize());
        writtenSize += fc.getSize();
    }
}
