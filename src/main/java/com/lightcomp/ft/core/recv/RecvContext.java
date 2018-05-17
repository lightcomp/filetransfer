package com.lightcomp.ft.core.recv;

import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

import com.lightcomp.ft.exception.TransferException;

public interface RecvContext {

    void setInputChannel(ReadableByteChannel rbch);

    Path getCurrentDir();

    Path getCurrentFile();

    void openDir(String name) throws TransferException;

    void closeDir() throws TransferException;

    void openFile(String name, long size) throws TransferException;

    void writeFileData(long offset, long length) throws TransferException;

    void closeFile(long lastModified) throws TransferException;
}
