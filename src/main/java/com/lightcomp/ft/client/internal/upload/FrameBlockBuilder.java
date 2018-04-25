package com.lightcomp.ft.client.internal.upload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.core.sender.FileBlockProcessor;
import com.lightcomp.ft.core.sender.SenderFrameContext;
import com.lightcomp.ft.core.sender.blocks.FileBlockProvider;
import com.lightcomp.ft.core.sender.items.SourceItem;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class FrameBlockProcessor {

    private final LinkedList<ProcessDir> dirStack = new LinkedList<>();

    private FileBlockProcessor fbProcessor;

    public FrameBlockProcessor(Iterator<SourceItem> srcItemIt) {
        addDir(srcItemIt, Paths.get(""));
    }

    public void process(SenderFrameContext frameCtx) {
        fbProcessor = getNextProcessor();
        while (true) {
            if (fbProcessor != null) {
                if (fbProcessor.isProcessed()) {
                    fbProcessor = getNextProcessor();
                    continue;
                }
                if (fbProcessor.addBlock(frameCtx)) {
                    continue;
                }
            }
            if () {
                
            }
         }
    }

    private FileBlockProcessor getNextProcessor() {
        // TODO Auto-generated method stub
        return null;
    }

    private FrameBlock createBlock() {
        // at least root has to exist
        Validate.isTrue(dirStack.size() > 0);

        Process dir = dirStack.getLast();
        if (dir.children != null && dir.children.hasNext()) {
            return createBlock(dir.children.next(), dir.path);
        }
        dirStack.removeLast();

        if (dirStack.isEmpty()) {
            return null; // exiting root directory
        }
        return new DirEndBlock();
    }

    private FrameBlock createBlock(SourceItem item, Path parentPath) {
        String name = item.getName();
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Empty source item name, parentPath=" + parentPath);
        }
        Path path = parentPath.resolve(name);
        if (item.isDir()) {
            addDir(item.asDir().getItemIterator(), path);
            return new DirBeginBlock(name);
        }
        FileBlockProvider fc = FileBlockProvider.create(item.asFile(), path.toString());
        return new FileBeginBlock(fc);
    }

    private void addDir(Iterator<SourceItem> it, Path path) {
        Process dir = new Process(it, path);
        dirStack.addLast(dir);
    }

    private static class ProcessDir {

        final Iterator<SourceItem> iterator;

        final Path path;

        ProcessDir(Iterator<SourceItem> iterator, Path path) {
            this.iterator = iterator;
            this.path = path;
        }
    }
}
