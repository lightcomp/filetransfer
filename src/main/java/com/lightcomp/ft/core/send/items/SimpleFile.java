package com.lightcomp.ft.core.send.items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleFile implements SourceFile {

	/*
	 * Path to file
	 */
    private final Path path;
    
    /*
     * Name of file. Last part of path is used when null
     */
    private final String name;

    /**
     * Construct SimpleFile. Use last part of path as name
     * @param path to file
     */
    public SimpleFile(Path path) {
        this(path,null);
    }

    /**
     * Construct SimpleFile with different name.
     * @param path to file
     * @param name alternative name of file
     */
    public SimpleFile(Path path, String name) {
    	this.path = path;
    	this.name = name;
    }
    
    @Override
    public String getName() {
    	if ( name != null ) {
    		return name;
    	} else {
    		return path.getFileName().toString();
    	}
    }

    @Override
    public boolean isDir() {
        return false;
    }

    @Override
    public SourceDir asDir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SourceFile asFile() {
        return this;
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] getChecksum() {
        return null;
    }

    @Override
    public ReadableByteChannel openChannel(long position) throws IOException {
    	SeekableByteChannel channel = Files.newByteChannel(path); //NOSONAR
    	channel.position(position);
    	return channel;
    }
}
