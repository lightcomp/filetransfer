package com.lightcomp.ft.server.internal.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.core.receiver.ReceiveContext;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class FrameProcessorImpl implements FrameProcessor, ReceiveContext {

    private static final Logger logger = LoggerFactory.getLogger(FrameProcessorImpl.class);

    private final byte[] chksmBuffer = new byte[ChecksumGenerator.LENGTH];

    private final FrameContext frameCtx;

    private final int seqNum;

    private final boolean last;

    private final long dataSize;

    private final Path dataFile;

    private final Collection<FrameBlock> blocks;

    private InputStream is;

    private long dataPos;

    public FrameProcessorImpl(int seqNum, boolean last, long dataSize, Collection<FrameBlock> blocks) {
        this.frameCtx = frameCtx;
        this.seqNum = seqNum;
        this.last = last;
        this.dataSize = dataSize;
        this.dataFile = dataFile;
        this.blocks = blocks;
    }

    @Override
    public int getSeqNum() {
        return seqNum;
    }

    @Override
    public long getDataSize() {
        return dataSize;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public void process() throws CanceledException {
        // process all blocks
        processBlocks();
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

    private void processBlocks() throws CanceledException {
        Validate.isTrue(is == null);
        try {
            is = Files.newInputStream(dataFile, StandardOpenOption.READ);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open frame data file").addParam("frameSeqNum", seqNum)
                    .addParam("path", dataFile).setCause(e).build();
        }
        int blockNum = 1;
        try {
            for (FrameBlock block : blocks) {
                if (frameCtx.isTransferCanceled()) {
                    throw new CanceledException();
                }
                try {
                    block.receive(this);
                } catch (Throwable t) {
                    throw TransferExceptionBuilder.from("Failed to process frame block").addParam("frameSeqNum", seqNum)
                            .addParam("blockNum", blockNum).setCause(t).build();
                }
                blockNum++;
            }
        } finally {
            cleanUp();
        }
    }

    private void cleanUp() {
        try {
            if (is != null) {
                is.close();
                is = null;
            }
            Files.delete(dataFile);
        } catch (IOException e) {
            logger.warn("Failed to clean up frame resources", e);
        }
    }

    /* block context methods */

    @Override
    public void openFile(String name, long size) {
        Path path = frameCtx.getCurrentDir().resolve(name);
        if (frameCtx.getCurrentFile() != null) {
            throw TransferExceptionBuilder.from("Can't open multiple files, current file must be closed first")
                    .addParam("path", path).build();
        }
        TransferFile tf = new TransferFile(path, size);
        frameCtx.setCurrentFile(tf);
    }

    @Override
    public void writeFileData(long offset, long size) {
        TransferFile file = frameCtx.getCurrentFile();
        if (file == null) {
            throw new TransferException("Failed to write data, no file currently open");
        }
        file.writeData(is, offset, size);
        dataPos += size;
    }

    @Override
    public void closeFile(long lastModified) {
        TransferFile file = frameCtx.getCurrentFile();
        if (file == null) {
            throw new TransferException("Failed to close file, no file currently open");
        }
        if (!file.isTransfered()) {
            throw TransferExceptionBuilder.from("Incomplete file cannot be closed").addParam("path", file.getPath()).build();
        }
        byte[] chksm = readFileChecksum();
        if (!Arrays.equals(file.getChecksum(), chksm)) {
            throw TransferExceptionBuilder.from("File checksum does not match").addParam("path", file.getPath())
                    .addParam("expectedChecksum", file.getChecksum()).addParam("receivedChecksum", chksm).build();
        }
        file.finish(lastModified);
        frameCtx.setCurrentFile(null);
    }

    private byte[] readFileChecksum() {
        int size = ChecksumGenerator.LENGTH;
        try {
            int n = is.read(chksmBuffer, 0, size);
            Validate.isTrue(n == size);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to read file cheksum from frame data").addParam("dataPos", dataPos)
                    .setCause(e).build();
        }
        dataPos += size;
        return chksmBuffer;
    }

    public static FrameProcessorImpl create(Frame frame, FrameContext frameCtx, Path dataFile) {
        boolean last = frame.isLast() != null ? frame.isLast() : false;
        Collection<FrameBlock> blocks = frame.getBlocks().getDesAndFdsAndFes();
        return new FrameProcessorImpl(frameCtx, frame.getSeqNum(), last, frame.getDataSize(), dataFile, blocks);
    }
}
