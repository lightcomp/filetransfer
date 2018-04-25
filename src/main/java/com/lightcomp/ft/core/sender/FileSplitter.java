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

class FileSplitter {

    private final SourceFile srcFile;

    private final Path path;

    private final ChecksumGenerator chksmGenerator;

    private final byte[] arrChksm;

    private long offset = -1;

    private FileSplitter(SourceFile srcFile, Path path, ChecksumGenerator chksmGenerator, byte[] arrChksm) {
        this.srcFile = srcFile;
        this.path = path;
        this.chksmGenerator = chksmGenerator;
        this.arrChksm = arrChksm;
    }

    /**
     * @return True when all remaining blocks were added to frame (blocks fits the
     *         frame).
     */
    public boolean prepareBlocks(SendFrameContext frameCtx) {
        while (offset <= srcFile.getSize()) {
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
        if (offset == srcFile.getSize()) {
            return addEndBlock(frameCtx);
        }
        return addDataBlock(frameCtx);
    }

    private boolean addBeginBlock(SendFrameContext frameCtx) {
        Validate.isTrue(srcFile.getSize() >= 0);
        Validate.notBlank(srcFile.getName());

        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileBeginBlockImpl b = new FileBeginBlockImpl();
        b.setFs(srcFile.getSize());
        b.setN(srcFile.getName());

        frameCtx.addBlock(b);
        offset = 0;

        return true;
    }

    private boolean addEndBlock(SendFrameContext frameCtx) {
        Validate.isTrue(srcFile.getLastModified() >= 0);

        long remFrameSize = frameCtx.getRemainingDataSize();
        long size = ChecksumGenerator.LENGTH;
        if (remFrameSize < size || frameCtx.isBlockListFull()) {
            return false;
        }
        FileEndBlockImpl b = new FileEndBlockImpl();
        b.setLm(srcFile.getLastModified());
        FileBlockStream bs = new FileChecksumStream(chksmGenerator, arrChksm);

        frameCtx.addBlock(b, bs);
        offset += size;

        return true;
    }

    private boolean addDataBlock(SendFrameContext frameCtx) {
        long remFrameSize = frameCtx.getRemainingDataSize();
        if (remFrameSize == 0 || frameCtx.isBlockListFull()) {
            return false;
        }
        long size = Math.min(remFrameSize, srcFile.getSize() - offset);

        FileDataBlockImpl b = new FileDataBlockImpl();
        b.setDs(size);
        b.setOff(offset);
        FileBlockStream bs = new FileDataStream(srcFile, path, offset, size, chksmGenerator);

        frameCtx.addBlock(b, bs);
        offset += size;

        return true;
    }

    public static FileSplitter create(SourceFile srcFile, Path path) {
        ChecksumGenerator chksmGenerator = null;
        byte[] arrChksm = null;
        // initialize checksum as array or his generator when not preset
        String chksm = StringUtils.trimToNull(srcFile.getChecksum());
        if (chksm == null) {
            chksmGenerator = ChecksumGenerator.create();
        } else {
            arrChksm = chksm.getBytes(StandardCharsets.UTF_8);
            if (arrChksm.length != ChecksumGenerator.LENGTH) {
                throw TransferExceptionBuilder.from("File checksum has invalid length").addParam("path", path)
                        .addParam("checksumLength", arrChksm.length).build();
            }
        }
        return new FileSplitter(srcFile, path, chksmGenerator, arrChksm);
    }
}
