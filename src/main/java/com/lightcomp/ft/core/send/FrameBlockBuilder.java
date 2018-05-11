package com.lightcomp.ft.core.send;

import java.util.Iterator;
import java.util.LinkedList;

import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceItem;

public class FrameBlockBuilder {

    private final LinkedList<DirContext> dirStack = new LinkedList<>();

    private final SendProgressInfo progressInfo;

    private FileSplitter currSplitter;

    public FrameBlockBuilder(Iterator<SourceItem> itemIt, SendProgressInfo progressInfo) {
        dirStack.add(DirContext.createRoot(itemIt));
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
            SourceItem item = dirCtx.getNextItem();
            if (item.isDir()) {
                dirCtx = DirContext.create(item.asDir(), dirCtx.getPath());
                dirStack.addLast(dirCtx);
            } else {
                currSplitter = FileSplitter.create(item.asFile(), dirCtx.getPath(), progressInfo);
            }
        }
        frameCtx.setLast(true);
    }

    /**
     * 
     * @param dirCtx
     * @param frameCtx
     * @return Return true if dirBegin was prepared. Return false if dirBegin cannot
     *         be add to the current frame (frame is full)
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
