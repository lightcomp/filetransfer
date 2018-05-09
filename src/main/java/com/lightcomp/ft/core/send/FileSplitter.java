package com.lightcomp.ft.core.send;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.core.blocks.FileBeginBlockImpl;
import com.lightcomp.ft.core.blocks.FileDataBlockImpl;
import com.lightcomp.ft.core.blocks.FileEndBlockImpl;
import com.lightcomp.ft.core.send.items.ChannelProvider;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

class FileSplitter {

    private final ChannelProvider channelProvider;

    private final String name;

    private final long size;

    private final long lastModified;

    private final ChecksumGenerator chksmGenerator;

    private final byte[] chksm;

    private final SendProgressInfo progressInfo;

    private final Path logPath;

    private long offset = -1;

    private FileSplitter(ChannelProvider channelProvider, String name, long size, long lastModified,
            ChecksumGenerator chksmGenerator, byte[] chksm, SendProgressInfo progressInfo, Path logPath) {
        this.channelProvider = channelProvider;
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.chksmGenerator = chksmGenerator;
        this.chksm = chksm;
        this.progressInfo = progressInfo;
        this.logPath = logPath;
    }

    /**
     * @return True when all remaining blocks were added to frame (blocks fits the
     *         frame).
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
        b.setFs(size);
        b.setN(name);

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
        b.setLm(lastModified);
        FrameBlockStream bs = new ChecksumStream(chksmGenerator, chksm);

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
        FrameBlockStream bs = new FileDataStream(channelProvider, offset, blockSize, chksmGenerator, progressInfo, logPath);

        frameCtx.addBlock(b, bs);
        offset += blockSize;

        return true;
    }

    public static FileSplitter create(SourceFile srcFile, SendProgressInfo progressInfo, Path logPath) {
        // validate base attributes
        String name = srcFile.getName();
        if (StringUtils.isBlank(name)) {
            throw TransferExceptionBuilder.from("Invalid file name").addParam("path", logPath).addParam("name", name).build();
        }
        long size = srcFile.getSize();
        if (size < 0) {
            throw TransferExceptionBuilder.from("Invalid file size").addParam("path", logPath).addParam("size", size).build();
        }
        long lastModified = srcFile.getLastModified();
        if (lastModified < 0 || lastModified > System.currentTimeMillis()) {
            throw TransferExceptionBuilder.from("Invalid last modification of file").addParam("path", logPath)
                    .addParam("lastModified", lastModified).build();
        }
        // validate checksum or initialize generator
        ChecksumGenerator chksmGenerator = null;
        byte[] chksm = srcFile.getChecksum();
        if (chksm != null) {
            if (chksm.length != ChecksumGenerator.LENGTH) {
                throw TransferExceptionBuilder.from("File checksum has invalid length").addParam("path", logPath)
                        .addParam("checksumLength", chksm.length).build();
            }
        } else {
            chksmGenerator = ChecksumGenerator.create();
        }
        return new FileSplitter(srcFile, name, size, lastModified, chksmGenerator, chksm, progressInfo, logPath);
    }
}
