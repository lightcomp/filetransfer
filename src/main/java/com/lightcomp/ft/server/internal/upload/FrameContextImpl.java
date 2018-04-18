package com.lightcomp.ft.server.internal.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class FrameContextImpl implements FrameContext {

    private final int seqNum;

    private final boolean last;

    private final long dataSize;

    private final Path dataFile;

    private final Collection<FrameBlock> blocks;

    private Path currentDir;

    private TransferFile currentFile;

    private long writtenDataSize;

    public FrameContextImpl(int seqNum, boolean last, long dataSize, Path dataFile, Collection<FrameBlock> blocks) {
        this.seqNum = seqNum;
        this.last = last;
        this.dataSize = dataSize;
        this.dataFile = dataFile;
        this.blocks = blocks;
    }

    public void init(FrameContextImpl prevFrameCtx) {
        if (prevFrameCtx == null) {
            return; // first frame
        }
        this.currentDir = prevFrameCtx.currentDir;
        this.currentFile = prevFrameCtx.currentFile;
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
    public Collection<FrameBlock> getBlocks() {
        return blocks;
    }

    @Override
    public InputStream openDataInputStream() throws IOException {
        return Files.newInputStream(dataFile, StandardOpenOption.READ);
    }

    @Override
    public void openDir(String name) {
        currentDir = currentDir.resolve(name);
    }

    @Override
    public void closeDir() {
        Path dir = currentDir.getParent();
        if (dir == null) {
            throw TransferExceptionBuilder.from("Directory tree inconsistency, trying to close root directory")
                    .addParam("frameSeqNum", seqNum).build();
        }
        currentDir = dir;
    }

    @Override
    public void openFile(String name, long size) {
        Path path = currentDir.resolve(name);
        if (currentFile != null) {
            throw TransferExceptionBuilder.from("File inconsistency, trying to open multiple files")
                    .addParam("frameSeqNum", seqNum).addParam("path", path).build();
        }
        currentFile = new TransferFile(path, size);
    }

    @Override
    public void closeFile(String checksum, long lastModified) {
        if (currentFile == null) {
            throw TransferExceptionBuilder.from("File inconsistency, trying to close non-existent file")
                    .addParam("frameSeqNum", seqNum).build();
        }
        if (!currentFile.isTransfered()) {
            throw TransferExceptionBuilder.from("Incomplete file cannot be closed").addParam("frameSeqNum", seqNum)
                    .addParam("path", currentFile.getPath()).build();
        }
        if (!currentFile.getChecksum().equals(checksum)) {
            throw TransferExceptionBuilder.from("File checksum does not match").addParam("frameSeqNum", seqNum)
                    .addParam("path", currentFile.getPath()).addParam("expectedChecksum", currentFile.getChecksum())
                    .addParam("receivedChecksum", checksum).build();
        }
        currentFile.finish(lastModified);
        currentFile = null;
    }

    @Override
    public void writeFileData(InputStream is, long frameOffset, long offset, long size) {
        if (currentFile == null) {
            throw TransferExceptionBuilder.from("File inconsistency, trying to write data for non-existent file")
                    .addParam("frameSeqNum", seqNum).build();
        }
        if (writtenDataSize != frameOffset) {
            throw TransferExceptionBuilder.from("Frame cannot be read in sequence, invalid offset")
                    .addParam("frameSeqNum", seqNum).addParam("frameOffset", frameOffset)
                    .addParam("writtenDataSize", writtenDataSize).build();
        }
        currentFile.writeData(is, offset, size);
        writtenDataSize += size;
    }

    public static FrameContextImpl create(Frame frame, Path dataFile) {
        boolean last = frame.isLast() != null ? frame.isLast() : false;
        Collection<FrameBlock> blocks = frame.getBlocks().getDesAndFdsAndFes();
        return new FrameContextImpl(frame.getSeqNum(), last, frame.getDataSize(), dataFile, blocks);
    }
}
