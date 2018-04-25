package com.lightcomp.ft.core.receiver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class ReceiveContextImpl implements ReceiveContext {

    private Path dirPath = Paths.get("");

    private FileWriter currFileWriter;

    private ReadableByteChannel frameByteChannel;

    public void setFrameByteChannel(ReadableByteChannel frameByteChannel) {
        this.frameByteChannel = frameByteChannel;
    }

    @Override
    public void openDir(String name) {
        Path path;
        try {
            path = dirPath.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid directory name").addParam("parentPath", dirPath).addParam("name", name)
                    .setCause(e).build();
        }
        try {
            Files.createDirectory(path);
        } catch (Throwable e) {
            throw TransferExceptionBuilder.from("Failed to create directory").addParam("path", path).setCause(e).build();
        }
        dirPath = path;
    }

    @Override
    public void closeDir() {
        Path path = dirPath.getParent();
        if (path == null) {
            throw TransferExceptionBuilder.from("Failed to close directory, transfer at root level").build();
        }
        dirPath = path;
    }

    @Override
    public void openFile(String name, long size) {
        if (currFileWriter != null) {
            throw TransferExceptionBuilder.from("Failed to open file, previous file must be closed first")
                    .addParam("previousFilePath", currFileWriter.getPath()).build();
        }
        Path path;
        try {
            path = dirPath.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid file name").addParam("dirPath", dirPath).addParam("name", name)
                    .setCause(e).build();
        }
        try {
            Files.createFile(path);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to create file").addParam("path", path).setCause(e).build();
        }
        currFileWriter = new FileWriter(path, size);
    }

    @Override
    public void writeFileData(long offset, long length) {
        currFileWriter.write(frameByteChannel, offset, length);
    }

    @Override
    public void closeFile(long lastModified) {
        if (currFileWriter == null) {
            throw TransferExceptionBuilder.from("Failed to close file, no current file found").addParam("dirPath", dirPath)
                    .build();
        }
        byte[] checksum;
        try {
            checksum = readFileChecksum();
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to read file checksum").addParam("path", currFileWriter.getPath())
                    .setCause(e).build();
        }
        currFileWriter.finish(lastModified, checksum);
    }

    private byte[] readFileChecksum() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(ChecksumGenerator.LENGTH);
        while (frameByteChannel.read(bb) > 0) {
        }
        if (bb.hasRemaining()) {
            throw new IOException("Frame stream ended prematurely");
        }
        return bb.array();
    }
}
