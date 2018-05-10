package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class RecvContextImpl implements RecvContext {

    private final RecvProgressInfo progressInfo;

    private final Path rootDir;

    private Path relativeDir;

    private FileWriter openWritter;

    private ReadableByteChannel inputChannel;

    public RecvContextImpl(RecvProgressInfo progressInfo, Path rootDir) {
        this.progressInfo = progressInfo;
        this.rootDir = rootDir;
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
    public void openDir(String name) {
        Path dir;
        try {
            dir = relativeDir.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid directory name").addParam("parentPath", relativeDir)
                    .addParam("name", name).setCause(e).build();
        }
        try {
            Path dstDir = rootDir.resolve(dir);
            Files.createDirectory(dstDir);
        } catch (Throwable e) {
            throw TransferExceptionBuilder.from("Failed to create directory").addParam("path", dir).setCause(e).build();
        }
        relativeDir = dir;
    }

    @Override
    public void closeDir() {
        if (PathUtils.ROOT.equals(relativeDir)) {
            throw new TransferException("Failed to close directory, transfer at root level");
        }
        Path dir = relativeDir.getParent();
        relativeDir = dir != null ? dir : PathUtils.ROOT;
    }

    @Override
    public void openFile(String name, long size) {
        if (openWritter != null) {
            throw TransferExceptionBuilder.from("Failed to open file, previous file must be closed first")
                    .addParam("previousFilePath", openWritter.getFile()).build();
        }
        Path file;
        try {
            file = relativeDir.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid file name").addParam("parentPath", relativeDir).addParam("name", name)
                    .setCause(e).build();
        }
        Path dstFile = rootDir.resolve(file);
        try {
            Files.createFile(dstFile);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to create file").addParam("path", file).setCause(e).build();
        }
        openWritter = new FileWriter(dstFile, size);
    }

    @Override
    public void writeFileData(long offset, long length) {
        openWritter.write(inputChannel, offset, length);
        progressInfo.onDataReceived(length);
    }

    @Override
    public void closeFile(long lastModified) {
        if (openWritter == null) {
            throw TransferExceptionBuilder.from("Failed to close file, no current file found").addParam("dirPath", relativeDir)
                    .build();
        }
        byte[] checksum;
        try {
            checksum = readFileChecksum();
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to read file checksum").addParam("path", openWritter.getFile())
                    .setCause(e).build();
        }
        openWritter.finish(lastModified, checksum);
        openWritter = null;
    }

    private byte[] readFileChecksum() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(ChecksumGenerator.LENGTH);
        while (inputChannel.read(bb) > 0) {
        }
        if (bb.hasRemaining()) {
            throw new IOException("Frame stream ended prematurely");
        }
        return bb.array();
    }
}
