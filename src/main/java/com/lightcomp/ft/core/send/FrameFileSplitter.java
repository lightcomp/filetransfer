package com.lightcomp.ft.core.send;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.lightcomp.ft.common.Checksum;
import com.lightcomp.ft.common.Checksum.Algorithm;
import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.ChecksumHolder;
import com.lightcomp.ft.core.blocks.FileBeginBlockImpl;
import com.lightcomp.ft.core.blocks.FileDataBlockImpl;
import com.lightcomp.ft.core.blocks.FileEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExBuilder;

class FrameFileSplitter {

	private final SourceFile srcFile;

	private final long size;

	private final Checksum checksum;

	private final Path srcPath;

	private final FileDataProgress dataProgress;

	private long offset = -1;

	private FrameFileSplitter(SourceFile srcFile, long size, Checksum checksum, Path srcPath,
			FileDataProgress dataProgress) {
		this.srcFile = srcFile;
		this.size = size;
		this.checksum = checksum;
		this.srcPath = srcPath;
		this.dataProgress = dataProgress;
	}

	/**
	 * @return True when all remaining blocks were added to frame (blocks fits the
	 *         frame).
	 */
	public boolean prepareBlocks(SendFrameContextImpl frameCtx) {
		while (offset <= size) {
			if (!addBlock(frameCtx)) {
				return false;
			}
		}
		return true;
	}

	private boolean addBlock(SendFrameContextImpl frameCtx) {
		if (offset < 0) {
			return addBeginBlock(frameCtx);
		}
		if (offset == size) {
			return addEndBlock(frameCtx);
		}
		return addDataBlock(frameCtx);
	}

	private boolean addBeginBlock(SendFrameContextImpl frameCtx) {
		if (frameCtx.isBlockListFull()) {
			return false;
		}
		FileBeginBlockImpl b = new FileBeginBlockImpl();
		b.setN(srcFile.getName());
		b.setFs(size);

		frameCtx.addBlock(b);
		offset = 0;

		return true;
	}

	private boolean addEndBlock(SendFrameContextImpl frameCtx) {
		long remFrameSize = frameCtx.getRemainingDataSize();
		long size = checksum.getAlgorithm().getByteLen();
		if (remFrameSize < size || frameCtx.isBlockListFull()) {
			return false;
		}
		FileEndBlockImpl b = new FileEndBlockImpl();
		b.setLm(srcFile.getLastModified());

		FileChksmStreamProvider fchsp = new FileChksmStreamProvider(checksum, srcPath);

		frameCtx.addBlock(b, fchsp);
		offset += size;

		return true;
	}

	private boolean addDataBlock(SendFrameContextImpl frameCtx) {
		long remFrameSize = frameCtx.getRemainingDataSize();
		if (remFrameSize == 0 || frameCtx.isBlockListFull()) {
			return false;
		}
		long blockSize = Math.min(remFrameSize, size - offset);

		FileDataBlockImpl b = new FileDataBlockImpl();
		b.setDs(blockSize);
		b.setOff(offset);

		FileDataStreamProvider fdsp = new FileDataStreamProvider(srcFile, offset, blockSize, checksum, srcPath,
				dataProgress);

		frameCtx.addBlock(b, fdsp);
		offset += blockSize;

		return true;
	}

	public static FrameFileSplitter create(SourceFile srcFile, Path parentPath, Algorithm checksumAlg,
			SendProgressInfo progressInfo) throws TransferException {
		// validate file name and create source path mainly for logging
		Path path;
		try {
			path = parentPath.resolve(srcFile.getName());
		} catch (InvalidPathException e) {
			throw new TransferExBuilder("Invalid source file name").addParam("parentPath", parentPath)
					.addParam("name", srcFile.getName()).setCause(e).build();
		}

		// validate file size and copy value to be sure nobody will change it
		long size = srcFile.getSize();
		if (size < 0) {
			throw new TransferExBuilder("Invalid source file size").addParam("path", path).addParam("size", size)
					.build();
		}

		Checksum checksum = createChecksum(checksumAlg, srcFile.getChecksum(), path);
		FileDataProgress dataProgress = new FileDataProgress(progressInfo);
		return new FrameFileSplitter(srcFile, size, checksum, path, dataProgress);
	}

	private static Checksum createChecksum(Algorithm checksumAlg, byte[] checksum, Path srcPath)
			throws TransferException {
		if (checksum != null) {
			if (checksumAlg.getByteLen() != checksum.length) {
				throw new TransferExBuilder("File checksum has invalid length")
						.addParam("algorithm", checksumAlg.getRealName())
						.addParam("definedLen", checksumAlg.getByteLen()).addParam("chksmLen", checksum.length).build();
			}
			return new ChecksumHolder(checksumAlg, checksum);
		}
		return ChecksumGenerator.create(checksumAlg);
	}
}
