package com.lightcomp.ft.core.sender;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.core.blocks.FileBeginBlockImpl;
import com.lightcomp.ft.core.blocks.FileDataBlockImpl;
import com.lightcomp.ft.core.blocks.FileEndBlockImpl;
import com.lightcomp.ft.core.sender.items.SourceFile;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class FileBlockProvider {

    private final SourceFile srcFile;

    private final Path srcPath;

    private ChecksumGenerator chksmGenerator;

    private byte[] arrChksm;

    private long offset = -1;

    private FileBlockProvider(SourceFile srcFile, Path srcPath) {
        this.srcFile = srcFile;
        this.srcPath = srcPath;
    }

    public boolean isProcessed() {
        return offset > srcFile.getSize();
    }

    /**
     * @param frameCtx
     *            not-null
     * @return False when block cannot be added because frame is full or file is
     *         already processed.
     */
    public boolean addBlock(SenderFrameContext frameCtx) {
        long size = srcFile.getSize();
        if (offset > size) {
            return false;
        }
        if (offset < 0) {
            return addBeginBlock(frameCtx);
        }
        if (offset == size) {
            return addEndBlock(frameCtx);
        }
        return addDataBlock(frameCtx);
    }

    private boolean addBeginBlock(SenderFrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileBeginBlockImpl b = new FileBeginBlockImpl();
        b.setN(srcFile.getName());
        b.setFs(srcFile.getSize());

        frameCtx.addBlock(b);
        offset = 0;

        return true;
    }

    private boolean addEndBlock(SenderFrameContext frameCtx) {
        long remFrameSize = frameCtx.getRemainingDataSize();
        long dataSize = ChecksumGenerator.BYTE_SIZE;
        if (remFrameSize < dataSize || frameCtx.isBlockListFull()) {
            return false;
        }
        FileEndBlockImpl b = new FileEndBlockImpl();
        b.setLm(srcFile.getLastModified());
        FileBlockStream bs = new FileChecksumStream(chksmGenerator, arrChksm);

        frameCtx.addBlock(b, bs);
        offset += dataSize;

        return true;
    }

    private boolean addDataBlock(SenderFrameContext frameCtx) {
        long remFrameSize = frameCtx.getRemainingDataSize();
        if (remFrameSize == 0 || frameCtx.isBlockListFull()) {
            return false;
        }
        long dataSize = Math.min(remFrameSize, srcFile.getSize() - offset);

        FileDataBlockImpl b = new FileDataBlockImpl();
        b.setDs(dataSize);
        b.setOff(offset);
        FileBlockStream bs = new FileDataStream(srcFile, srcPath, offset, dataSize, chksmGenerator);

        frameCtx.addBlock(b, bs);
        offset += dataSize;

        return true;
    }

    public static FileBlockProvider create(SourceFile srcFile, Path dirPath) {
        Validate.isTrue(srcFile.getLastModified() >= 0);
        Validate.isTrue(srcFile.getSize() >= 0);

        Path srcPath = resolvePath(srcFile.getName(), dirPath);
        FileBlockProvider ctx = new FileBlockProvider(srcFile, srcPath);
        initChecksum(ctx);
        return ctx;
    }

    private static void initChecksum(FileBlockProvider ctx) {
        String chksm = srcFile.getChecksum();
        if (StringUtils.isEmpty(chksm)) {
            ctx.chksmGenerator = ChecksumGenerator.create();
        } else {
            ctx.arrChksm = chksm.getBytes(StandardCharsets.UTF_8);
            if (ctx.arrChksm.length != ChecksumGenerator.BYTE_SIZE) {
                throw TransferExceptionBuilder.from("File checksum has invalid length").addParam("path", srcPath)
                        .addParam("checksumLength", ctx.arrChksm.length).build();
            }
        }
    }

    private static Path resolvePath(String fileName, Path dirPath) {
        Validate.notNull(dirPath);
        try {
            return dirPath.resolve(fileName);
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Invalid source file name").addParam("fileName", fileName)
                    .addParam("dirPath", dirPath).setCause(t).build();
        }
    }
}
