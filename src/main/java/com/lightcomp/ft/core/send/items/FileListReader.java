package com.lightcomp.ft.core.send.items;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.send.items.SimpleFile;
import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.core.send.items.SourceItemReader;

public class FileListReader implements SourceItemReader {

    static final Logger log = LoggerFactory.getLogger(FileListReader.class);

    final Path root;

    final Path fileList;

    BufferedReader br = null;

    boolean prepared = false;

    SourceItem nextItem = null;

    public FileListReader(Path root, Path fileList) {
        this.root = root;
        this.fileList = fileList;
    }

    @Override
    public void open() {
        try {
            br = Files.newBufferedReader(fileList);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasNext() {
        if (prepared == false) {
            String line;
            try {
                line = br.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (line == null) {
                return false;
            }
            nextItem = readNextItem(line);
            prepared = true;
        }
        return nextItem != null;
    }

    private SourceItem readNextItem(String line) {
        String splitted[] = line.split("\\|");
        if (splitted == null || splitted.length == 0) {
            throw new IllegalStateException("Empty line");
        }
        switch (splitted[0]) {
        case "F":
            if (splitted.length != 3) {
                throw new IllegalStateException();
            }
            return new SimpleFile(root.resolve(splitted[2]), splitted[1]);
        case "D":
            if (splitted.length != 2) {
                throw new IllegalStateException();
            }
            return new DirReader(splitted[1]);
        case "E":
            return null;
        default:
            throw new IllegalStateException("Unsupported operation " + splitted[0]);
        }
    }

    @Override
    public SourceItem getNext() {
        if ( prepared == false ) {
            throw new IllegalStateException("Item not prepared");
        }
        prepared = false;
        SourceItem tmpNextItem = nextItem;
        nextItem = null;
        return tmpNextItem;
    }

    @Override
    public void close() {
        try {
            br.close();
        } catch (IOException e) {
            log.error("Fail to close file", e);
        }
    }

    class DirReader implements SourceDir {

        final String name;

        public DirReader(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isDir() {
            return true;
        }

        @Override
        public SourceDir asDir() {
            return this;
        }

        @Override
        public SourceFile asFile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SourceItemReader getChidrenReader() {
            return new ChildReader();
        }

    }

    class ChildReader implements SourceItemReader {

        SourceItem nextItem = null;

        boolean prepared = false;

        @Override
        public void open() {
        }

        @Override
        public boolean hasNext() {
            if (prepared == false) {
                String line;
                try {
                    line = br.readLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (line == null) {
                    return false;
                }
                nextItem = readNextItem(line);
                prepared = true;
            }
            return nextItem != null;
        }

        @Override
        public SourceItem getNext() {
            if ( prepared == false ) {
                throw new IllegalStateException("Item not prepared");
            }
            prepared = false;
            SourceItem tmpItem = nextItem;
            nextItem = null;
            return tmpItem;
        }

        @Override
        public void close() {
        }

    }

}
