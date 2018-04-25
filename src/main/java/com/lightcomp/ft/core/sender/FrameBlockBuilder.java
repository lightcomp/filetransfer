package com.lightcomp.ft.core.sender;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;

import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.sender.items.SourceDir;
import com.lightcomp.ft.core.sender.items.SourceFile;
import com.lightcomp.ft.core.sender.items.SourceItem;

public class FrameBlockBuilder {

    private final LinkedList<DirContext> dirStack = new LinkedList<>();

    private FileSplitter currFileSpltr;

    public FrameBlockBuilder(Iterator<SourceItem> sourceItemIt) {
        dirStack.add(new DirContext(Paths.get(""), sourceItemIt));
    }

    public void build(SendFrameContext frameCtx) {
        while (dirStack.size() > 0) {
            // add all blocks from current file first
            if (currFileSpltr != null) {
                if (!currFileSpltr.prepareBlocks(frameCtx)) {
                    return; // frame filled
                }
                currFileSpltr = null;
            }
            // get last directory and create begin/end if needed
            DirContext dirCtx = dirStack.getLast();
            if (!dirCtx.isStarted()) {
                if (!prepareDirBegin(dirCtx, frameCtx)) {
                    return; // frame filled
                }
            }
            if (!dirCtx.hasNextItem()) {
                if (!prepareDirEnd(dirCtx, frameCtx)) {
                    return; // frame filled
                }
                dirStack.removeLast();
                continue;
            }
            // last directory didn't end -> process next child
            SourceItem child = dirCtx.getNextItem();
            if (child.isDir()) {
                addDir(dirCtx.getPath(), child.asDir());
            } else {
                setFile(dirCtx.getPath(), child.asFile());
            }
        }
        frameCtx.setLast(true);
    }

    private boolean prepareDirBegin(DirContext dirCtx, SendFrameContext frameCtx) {
        if (dirStack.size() == 1) {
            return true;
        }
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        DirBeginBlockImpl b = new DirBeginBlockImpl();
        b.setN(dirCtx.getName());
        frameCtx.addBlock(b);
        dirCtx.setStarted(true);
        return true;
    }

    private boolean prepareDirEnd(DirContext dirCtx, SendFrameContext frameCtx) {
        if (dirStack.size() == 1) {
            return true;
        }
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        DirEndBlockImpl b = new DirEndBlockImpl();
        frameCtx.addBlock(b);
        return true;
    }

    private void addDir(Path parentPath, SourceDir srcDir) {
        Path path = parentPath.resolve(srcDir.getName());
        DirContext dirCtx = new DirContext(path, srcDir.getItemIterator());
        dirStack.addLast(dirCtx);
    }

    private void setFile(Path parentPath, SourceFile srcFile) {
        Path path = parentPath.resolve(srcFile.getName());
        currFileSpltr = FileSplitter.create(srcFile, path);
    }
}
