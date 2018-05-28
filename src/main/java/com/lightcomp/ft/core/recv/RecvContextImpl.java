package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.Checksum.Algorithm;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class RecvContextImpl implements RecvContext {

    private static final Logger logger = LoggerFactory.getLogger(RecvContextImpl.class);

    private final RecvProgressInfo progressInfo;

    private final Path rootDir;

    private final Algorithm checksumAlg;

    private Path relativeDir;

    private FileWriter openWritter;

    private ReadableByteChannel inputChannel;

    public RecvContextImpl(RecvProgressInfo progressInfo, Path rootDir, Algorithm checksumAlg) {
        this.progressInfo = progressInfo;
        this.rootDir = rootDir;
        this.checksumAlg = checksumAlg;
        this.relativeDir = PathUtils.ROOT;
    }

    @Override
    public void setInputChannel(ReadableByteChannel inputChannel) {
        this.inputChannel = inputChannel;
    }

    @Override
    public Path getCurrentDir() {
        return PathUtils.ROOT.equals(relativeDir) ? null : relativeDir;
    }

    @Override
    public Path getCurrentFile() {
        return openWritter == null ? null : openWritter.getFile();
    }

    @Override
    public void openDir(String name) throws TransferException {
        if (logger.isDebugEnabled()) {
            logger.debug("Open directory '{}' in '{}'", name, relativeDir);
        }
        Path dir;
        try {
            dir = relativeDir.resolve(name);
        } catch (InvalidPathException e) {
            throw new TransferExceptionBuilder("Invalid directory name").addParam("parentPath", relativeDir)
                    .addParam("name", name).setCause(e).build();
        }
        try {
            Path dstDir = rootDir.resolve(dir);
            Files.createDirectory(dstDir);
        } catch (Throwable e) {
            throw new TransferExceptionBuilder("Failed to create directory").addParam("path", dir).setCause(e).build();
        }
        relativeDir = dir;
    }

    @Override
    public void closeDir() throws TransferException {
        if (PathUtils.ROOT.equals(relativeDir)) {
            throw new TransferException("Failed to close directory, transfer at root level");
        }
        Path dir = relativeDir.getParent();
        relativeDir = dir != null ? dir : PathUtils.ROOT;
    }

    @Override
    public void openFile(String name, long size) throws TransferException {
        if (openWritter != null) {
            throw new TransferExceptionBuilder("Failed to open file, previous file must be closed first")
                    .addParam("previousFilePath", openWritter.getFile()).build();
        }
        Path file;
        try {
            file = relativeDir.resolve(name);
        } catch (InvalidPathException e) {
            throw new TransferExceptionBuilder("Invalid file name").addParam("parentPath", relativeDir)
                    .addParam("name", name).setCause(e).build();
        }
        Path dstFile = rootDir.resolve(file);
        try {
            Files.createFile(dstFile);
        } catch (IOException e) {
            throw new TransferExceptionBuilder("Failed to create file").addParam("path", file).setCause(e).build();
        }
        openWritter = new FileWriter(dstFile, size, checksumAlg);
    }

    @Override
    public void writeFileData(long offset, long length) throws TransferException {
        openWritter.write(inputChannel, offset, length);
        progressInfo.onFileDataReceived(length);
    }

    @Override
    public void closeFile(long lastModified) throws TransferException {
        if (openWritter == null) {
            throw new TransferExceptionBuilder("Failed to close file, no current file found")
                    .addParam("dirPath", relativeDir).build();
        }
        byte[] checksum;
        try {
            checksum = readFileChecksum();
        } catch (IOException e) {
            throw new TransferExceptionBuilder("Failed to read file checksum").addParam("path", openWritter.getFile())
                    .setCause(e).build();
        }
        openWritter.finish(lastModified, checksum);
        openWritter = null;
    }

    private byte[] readFileChecksum() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(checksumAlg.getByteLen());
        while (inputChannel.read(bb) > 0) {
            // read while channel has data and buffer is not full
        }
        if (bb.hasRemaining()) {
            throw new IOException("Frame stream ended prematurely");
        }
        return bb.array();
    }
}
