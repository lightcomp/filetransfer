package com.lightcomp.ft.receiver.impl.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.receiver.impl.TransferContext;
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
        List<FileChunk> fileChunks = frame.getFileChunks().getList();
        if (fileChunks.isEmpty()) {
            return;
        }
        try (InputStream is = frame.getData().getInputStream()) {
            for (FileChunk fc : fileChunks) {
                if (transferCtx.isCancelRequested()) {
                    throw new CanceledException();
                }
                writeFileChunk(fc, is);
            }
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open frame input stream").addParam("frameId", frame.getFrameId())
                    .setCause(e).build();
        }
    }

    private void writeFileChunk(FileChunk fc, InputStream is) {
        if (writtenSize != fc.getFrameOffset()) {
            throw TransferExceptionBuilder.from("Current file chunk does not describe next frame data")
                    .addParam("frameId", frame.getFrameId()).addParam("fileId", fc.getFileId())
                    .addParam("writtenSize", writtenSize).addParam("frameOffset", fc.getFrameOffset()).build();
        }
        TransferFile tf = transferCtx.getFile(fc.getFileId());
        tf.writeChunk(is, fc.getOffset(), fc.getSize());
        writtenSize += fc.getSize();
    }
}
