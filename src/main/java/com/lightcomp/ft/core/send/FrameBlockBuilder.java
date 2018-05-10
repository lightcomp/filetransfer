package com.lightcomp.ft.core.send;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedList;

import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class FrameBlockBuilder {

    private final LinkedList<DirContext> dirStack = new LinkedList<>();

    private final SendProgressInfo progressInfo;

    private FileSplitter currSplitter;

    public FrameBlockBuilder(Iterator<SourceItem> rootItemIt, SendProgressInfo progressInfo) {
        // add root folder
        dirStack.add(new DirContext(null, PathUtils.ROOT, rootItemIt));
        this.progressInfo = progressInfo;
    }

    public void build(SendFrameContext frameCtx) {
        while (dirStack.size() > 0) {
            // add all blocks from current file first
            if (currSplitter != null) {
                if (!currSplitter.prepareBlocks(frameCtx)) {
                    return; // frame filled
                }
                currSplitter = null;
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
                addDir(child.asDir(), dirCtx.getPath());
            } else {
                currSplitter = FileSplitter.create(child.asFile(), progressInfo, dirCtx.getPath());
            }
        }
        frameCtx.setLast(true);
    }

    /**
     * 
     * @param dirCtx
     * @param frameCtx
     * @return Return true if dirBegin was prepared.
     *   Return false if dirBegin cannot be add to the current frame (frame is full)
     */
    private boolean prepareDirBegin(DirContext dirCtx, SendFrameContext frameCtx) {
    	// Do not send dirBegin for root folder
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

    private void addDir(SourceDir srcDir, Path parentPath) {
        String name = srcDir.getName();
        Path path;
        try {
            path = parentPath.resolve(name);
        } catch (InvalidPathException e) {
            throw TransferExceptionBuilder.from("Invalid source directory name").addParam("parentPath", parentPath)
                    .addParam("name", name).setCause(e).build();
        }
        DirContext dirCtx = new DirContext(name, path, srcDir.getItemIterator());
        dirStack.addLast(dirCtx);
    }
}
