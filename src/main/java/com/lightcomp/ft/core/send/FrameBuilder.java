package com.lightcomp.ft.core.send;

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.exception.TransferException;

/**
 * Frame builder, builds frame in sequence from first to last one. Implementation is not thread
 * safe.
 */
public class FrameBuilder {

    private final LinkedList<DirContext> dirStack = new LinkedList<>();

    private final SendProgressInfo progressInfo;

    private final SendConfig config;

    private FileSplitter currSplitter;

    private int currSeqNum;

    public FrameBuilder(Iterator<SourceItem> itemIt, SendProgressInfo progressInfo, SendConfig config) {
        dirStack.add(DirContext.createRoot(itemIt));
        this.progressInfo = progressInfo;
        this.config = config;
    }

    public Object getCurrentSeqNum() {
        return currSeqNum;
    }

    public SendFrameContext build() throws TransferException {
        Validate.isTrue(dirStack.size() > 0, "Last frame already built");

        currSeqNum++;
        SendFrameContextImpl frameCtx = new SendFrameContextImpl(currSeqNum, config);
        buildBlocks(frameCtx);
        return frameCtx;
    }

    private void buildBlocks(SendFrameContextImpl frameCtx) throws TransferException {
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
                if (!addDirBegin(dir, frameCtx)) {
                    return; // frame filled
                }
            }
            if (!dir.hasNextItem()) {
                if (!addDirEnd(dir, frameCtx)) {
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
                currSplitter = FileSplitter.create(item.asFile(), dir.getPath(), progressInfo, config.getChecksumAlg());
            }
        }
        frameCtx.setLast(true);
    }

    /**
     * @return Return true if dir begin block was added. Return false if frame is full.
     */
    private boolean addDirBegin(DirContext dirCtx, SendFrameContextImpl frameCtx) {
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

    /**
     * @return Return true if dir end block was added. Return false if frame is full.
     */
    private boolean addDirEnd(DirContext dirCtx, SendFrameContextImpl frameCtx) {
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
