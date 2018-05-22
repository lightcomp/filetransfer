package com.lightcomp.ft.server;

import java.nio.file.Path;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.SimpleIdGenerator;
import com.lightcomp.ft.core.TransferIdGenerator;

/**
 * Server configuration.
 */
public class ServerConfig {

    private final TransferStatusStorage transferStatusStorage;

    private final TransferHandler transferHandler;

    private TransferIdGenerator transferIdGenerator = new SimpleIdGenerator();

    private Path workDir = PathUtils.SYS_TEMP;

    private int threadPoolSize = 50;

    private int inactiveTimeout = 60 * 5;

    private long maxFrameSize = 10 * 1024 * 1024;

    private int maxFrameBlocks = 10000;

    public ServerConfig(TransferHandler transferHandler, TransferStatusStorage transferStatusStorage) {
        this.transferHandler = Validate.notNull(transferHandler);
        this.transferStatusStorage = Validate.notNull(transferStatusStorage);
    }

    public TransferHandler getTransferHandler() {
        return transferHandler;
    }

    public TransferStatusStorage getTransferStatusStorage() {
        return transferStatusStorage;
    }

    public TransferIdGenerator getTransferIdGenerator() {
        return transferIdGenerator;
    }

    public void setTransferIdGenerator(TransferIdGenerator transferIdGenerator) {
        this.transferIdGenerator = transferIdGenerator;
    }

    /**
     * @return Work directory for transfer data.
     */
    public Path getWorkDir() {
        return workDir;
    }

    /**
     * @param workDir
     *            work directory for transfer data, not-null
     */
    public void setWorkDir(Path workDir) {
        Validate.notNull(workDir);
        this.workDir = workDir;
    }

    /**
     * @return Maximum of simultaneously running tasks.
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * @param threadPoolSize
     *            maximum of simultaneously running tasks, greater than zero
     */
    public void setThreadPoolSize(int threadPoolSize) {
        Validate.isTrue(threadPoolSize > 0);
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * @return Number of seconds until transfer is consider inactive.
     */
    public int getInactiveTimeout() {
        return inactiveTimeout;
    }

    /**
     * @param inactiveTimeout
     *            number of seconds until transfer is consider inactive, greater than zero
     */
    public void setInactiveTimeout(int inactiveTimeout) {
        Validate.isTrue(inactiveTimeout > 0);
        this.inactiveTimeout = inactiveTimeout;
    }

    /**
     * @return Maximum frame size in bytes.
     */
    public long getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * @param maxFrameSize
     *            maximum frame size in bytes, greater than zero
     */
    public void setMaxFrameSize(long maxFrameSize) {
        Validate.isTrue(maxFrameSize > 0);
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * @return Maximum number of blocks in one frame.
     */
    public int getMaxFrameBlocks() {
        return maxFrameBlocks;
    }

    /**
     * @param maxFrameBlocks
     *            maximum number of blocks in one frame, greater than zero
     */
    public void setMaxFrameBlocks(int maxFrameBlocks) {
        Validate.isTrue(maxFrameBlocks > 0);
        this.maxFrameBlocks = maxFrameBlocks;
    }
}
