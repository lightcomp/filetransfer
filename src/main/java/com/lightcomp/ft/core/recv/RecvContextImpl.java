package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class RecvContextImpl implements RecvContext {

    private static final Path ROOT_DIR = Paths.get("");

    private final RecvProgressInfo progressInfo;

    private final Path rootDir;

    private Path currDir;

    private FileWriter currFileWritter;

    private ReadableByteChannel rbch;

    public RecvContextImpl(RecvProgressInfo progressInfo, Path rootDir) {
        this.progressInfo = progressInfo;
        this.rootDir = rootDir;
        this.currDir = ROOT_DIR;
    }

    @Override
    public void setInputChannel(ReadableByteChannel rbch) {
        this.rbch = rbch;
    }

    @Override
    public Path getCurrentDir() {
        return ROOT_DIR.equals(currDir) ? null : currDir;
    }

    @Override
    public Path getCurrentFile() {
        return currFileWritter != null ? currFileWritter.getFile() : null;
    }

    @Override
    public void openDir(String name) {
        Path dir;
        try {
            dir = currDir.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid directory name").addParam("parentPath", currDir).addParam("name", name)
                    .setCause(e).build();
        }
        try {
            Path dstDir = rootDir.resolve(dir);
            Files.createDirectory(dstDir);
        } catch (Throwable e) {
            throw TransferExceptionBuilder.from("Failed to create directory").addParam("path", dir).setCause(e).build();
        }
        currDir = dir;
    }

    @Override
    public void closeDir() {
        if (ROOT_DIR.equals(currDir)) {
            throw new TransferException("Failed to close directory, transfer at root level");
        }
        Path dir = currDir.getParent();
        currDir = dir != null ? dir : ROOT_DIR;
    }

    @Override
    public void openFile(String name, long size) {
        if (currFileWritter != null) {
            throw TransferExceptionBuilder.from("Failed to open file, previous file must be closed first")
                    .addParam("previousFilePath", currFileWritter.getFile()).build();
        }
        Path file;
        try {
            file = currDir.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid file name").addParam("dirPath", currDir).addParam("name", name)
                    .setCause(e).build();
        }
        Path dstFile = rootDir.resolve(file);
        try {
            Files.createFile(dstFile);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to create file").addParam("path", file).setCause(e).build();
        }
        currFileWritter = new FileWriter(dstFile, size);
    }

    @Override
    public void writeFileData(long offset, long length) {
        currFileWritter.write(rbch, offset, length);
        progressInfo.onDataReceived(length);
    }

    @Override
    public void closeFile(long lastModified) {
        if (currFileWritter == null) {
            throw TransferExceptionBuilder.from("Failed to close file, no current file found").addParam("dirPath", currDir)
                    .build();
        }
        byte[] checksum;
        try {
            checksum = readFileChecksum();
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to read file checksum").addParam("path", currFileWritter.getFile())
                    .setCause(e).build();
        }
        currFileWritter.finish(lastModified, checksum);
        currFileWritter = null;
    }

    private byte[] readFileChecksum() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(ChecksumGenerator.LENGTH);
        while (rbch.read(bb) > 0) {
        }
        if (bb.hasRemaining()) {
            throw new IOException("Frame stream ended prematurely");
        }
        return bb.array();
    }
}
