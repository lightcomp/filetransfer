package com.lightcomp.ft.core.send;

import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.core.send.items.SourceItemReader;
import com.lightcomp.ft.exception.TransferException;

/**
 * Frame builder, builds frame in sequence from first to last one.
 * Implementation is not thread safe.
 */
public class FrameBuilder {

	private final LinkedList<FrameDirContext> dirStack = new LinkedList<>();

	private final SendConfig config;

	private SendProgressInfo progressInfo;

	private FrameFileSplitter currFS;

	private int currSeqNum;

	public FrameBuilder(SendProgressInfo progressInfo, SendConfig config) {
		this.progressInfo = progressInfo;
		this.config = config;
	}

	public int getCurrentSeqNum() {
		return currSeqNum;
	}

	public void init(SourceItemReader rootItemsReader) {
		Validate.isTrue(currSeqNum == 0);

		dirStack.add(new FrameDirContext(null, PathUtils.ROOT, rootItemsReader));
	}

	public SendFrameContext build() throws TransferException {
		Validate.isTrue(dirStack.size() > 0); // initialization not called or last frame built

		currSeqNum++;
		SendFrameContextImpl frameCtx = new SendFrameContextImpl(currSeqNum, config);
		buildBlocks(frameCtx);
		return frameCtx;
	}

	private void buildBlocks(SendFrameContextImpl frameCtx) throws TransferException {
		while (dirStack.size() > 0) {
			// add all blocks from current file first
			if (currFS != null) {
				if (!currFS.prepareBlocks(frameCtx)) {
					return; // frame filled
				}
				currFS = null;
			}
			// get current directory
			FrameDirContext dir = dirStack.getLast();
			// add begin block if needed
			if (!dir.isOpen()) {
				if (!addDirBegin(dir, frameCtx)) {
					return; // frame filled
				}
				dir.open();
			}
			// process next child if present
			if (dir.hasNext()) {
				SourceItem child = dir.getNext();
				if (child.isDir()) {
					FrameDirContext childDir = new FrameDirContext(child.getName(), dir.getPath(),
							child.asDir().getChidrenReader());
					dirStack.addLast(childDir);
				} else {
					currFS = FrameFileSplitter.create(child.asFile(), dir.getPath(), config.getChecksumAlg(),
							progressInfo);
				}
				continue;
			}
			// add end block and remove it from stack
			if (!addDirEnd(dir, frameCtx)) {
				return; // frame filled
			}
			dir.close();
			dirStack.removeLast();
		}
		frameCtx.setLast(true);
	}

	/**
	 * @return Return true if dir begin block was added. Return false if frame is
	 *         full.
	 */
	private boolean addDirBegin(FrameDirContext dirCtx, SendFrameContextImpl frameCtx) {
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
		return true;
	}

	/**
	 * @return Return true if dir end block was added. Return false if frame is
	 *         full.
	 */
	private boolean addDirEnd(FrameDirContext dirCtx, SendFrameContextImpl frameCtx) {
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
	
	/**
	 * Close remaining opened FrameDirContext placed in dirStack  
	 */
	public void closeBuilder() {
		for(FrameDirContext dir:dirStack) {
			dir.close();
		}
	}
}
