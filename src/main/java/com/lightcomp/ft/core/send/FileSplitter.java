package com.lightcomp.ft.core.send;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.core.blocks.FileBeginBlockImpl;
import com.lightcomp.ft.core.blocks.FileDataBlockImpl;
import com.lightcomp.ft.core.blocks.FileEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

class FileSplitter {

    private final SourceFile srcFile;

    private final long size;

    private final ChecksumGenerator chksmGenerator;

    private final byte[] chksm;

    private final SendProgressInfo progressInfo;

    private final Path logPath;

    private long offset = -1;

    private FileSplitter(SourceFile srcFile, long size, ChecksumGenerator chksmGenerator, byte[] chksm,
            SendProgressInfo progressInfo, Path logPath) {
        this.srcFile = srcFile;
        this.size = size;
        this.chksmGenerator = chksmGenerator;
        this.chksm = chksm;
        this.progressInfo = progressInfo;
        this.logPath = logPath;
    }

    /**
     * @return True when all remaining blocks were added to frame (blocks fits the frame).
     */
    public boolean prepareBlocks(SendFrameContext frameCtx) {
        while (offset <= size) {
            if (!addBlock(frameCtx)) {
                return false;
            }
        }
        return true;
    }

    private boolean addBlock(SendFrameContext frameCtx) {
        if (offset < 0) {
            return addBeginBlock(frameCtx);
        }
        if (offset == size) {
            return addEndBlock(frameCtx);
        }
        return addDataBlock(frameCtx);
    }

    private boolean addBeginBlock(SendFrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileBeginBlockImpl b = new FileBeginBlockImpl();
        b.setN(srcFile.getName());
        b.setFs(size);

        frameCtx.addBlock(b);
        offset = 0;

        return true;
    }

    private boolean addEndBlock(SendFrameContext frameCtx) {
        long remFrameSize = frameCtx.getRemainingDataSize();
        long size = ChecksumGenerator.LENGTH;
        if (remFrameSize < size || frameCtx.isBlockListFull()) {
            return false;
        }
        FileEndBlockImpl b = new FileEndBlockImpl();
        b.setLm(srcFile.getLastModified());
        FrameBlockStream bs = new FileChksmStream(chksmGenerator, chksm, logPath);

        frameCtx.addBlock(b, bs);
        offset += size;

        return true;
    }

    private boolean addDataBlock(SendFrameContext frameCtx) {
        long remFrameSize = frameCtx.getRemainingDataSize();
        if (remFrameSize == 0 || frameCtx.isBlockListFull()) {
            return false;
        }
        long blockSize = Math.min(remFrameSize, size - offset);

        FileDataBlockImpl b = new FileDataBlockImpl();
        b.setDs(blockSize);
        b.setOff(offset);
        FrameBlockStream bs = new FileDataStream(srcFile, offset, blockSize, chksmGenerator, progressInfo, logPath);

        frameCtx.addBlock(b, bs);
        offset += blockSize;

        return true;
    }

    public static FileSplitter create(SourceFile srcFile, Path parentPath, SendProgressInfo progressInfo)
            throws TransferException {
        // validate base attributes
        Path path;
        try {
            path = parentPath.resolve(srcFile.getName());
        } catch (InvalidPathException e) {
            throw new TransferExceptionBuilder("Invalid source file name").addParam("parentPath", parentPath)
                    .addParam("name", srcFile.getName()).setCause(e).build();
        }
        long size = srcFile.getSize();
        if (size < 0) {
            throw new TransferExceptionBuilder("Invalid source file size").addParam("path", path).addParam("size", size).build();
        }
        // validate checksum or initialize generator
        ChecksumGenerator chksmGenerator = null;
        byte[] chksm = srcFile.getChecksum();
        if (chksm != null) {
            if (chksm.length != ChecksumGenerator.LENGTH) {
                throw new TransferExceptionBuilder("File checksum has invalid length").addParam("path", path)
                        .addParam("checksumLength", chksm.length).build();
            }
        } else {
            chksmGenerator = ChecksumGenerator.create();
        }
        return new FileSplitter(srcFile, size, chksmGenerator, chksm, progressInfo, path);
    }
}
