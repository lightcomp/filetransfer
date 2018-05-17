package com.lightcomp.ft.core.send;

import java.util.Iterator;
import java.util.LinkedList;

import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.exception.TransferException;

public class FrameBlockBuilder {

    private final LinkedList<DirContext> dirStack = new LinkedList<>();

    private final SendProgressInfo progressInfo;

    private FileSplitter currSplitter;

    public FrameBlockBuilder(Iterator<SourceItem> itemIt, SendProgressInfo progressInfo) {
        dirStack.add(DirContext.createRoot(itemIt));
        this.progressInfo = progressInfo;
    }

    public void build(SendFrameContext frameCtx) throws TransferException {
        while (dirStack.size() > 0) {
            // add all blocks from current file first
            if (currSplitter != null) {
                if (!currSplitter.prepareBlocks(frameCtx)) {
                    return; // frame filled
                }
                currSplitter = null;
            }
            // get last directory and create begin/end if needed
            DirContext dir = dirStack.getLast();
            if (!dir.isStarted()) {
                if (!prepareDirBegin(dir, frameCtx)) {
                    return; // frame filled
                }
            }
            if (!dir.hasNextItem()) {
                if (!prepareDirEnd(dir, frameCtx)) {
                    return; // frame filled
                }
                dirStack.removeLast();
                continue;
            }
            // last directory didn't end -> process next child
            SourceItem item = dir.getNextItem();
            if (item.isDir()) {
                DirContext childDir = DirContext.create(item.asDir(), dir.getPath());
                dirStack.addLast(childDir);
            } else {
                currSplitter = FileSplitter.create(item.asFile(), dir.getPath(), progressInfo);
            }
        }
        frameCtx.setLast(true);
    }

    /**
     * 
     * @param dirCtx
     * @param frameCtx
     * @return Return true if dirBegin was prepared. Return false if dirBegin cannot be add to the current frame (frame is full)
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
}
