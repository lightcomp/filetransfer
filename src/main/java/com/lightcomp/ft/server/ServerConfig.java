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

    private TransferIdGenerator transferIdGenerator = new SimpleIdGenerator();

    private TransferStatusStorage transferStatusStorage;

    private TransferHandler transferHandler;

    private Path workDir = PathUtils.SYS_TEMP;

    private int threadPoolSize = 50;

    private int inactiveTimeout = 60 * 5;

    public TransferIdGenerator getTransferIdGenerator() {
        return transferIdGenerator;
    }

    public void setTransferIdGenerator(TransferIdGenerator transferIdGenerator) {
        this.transferIdGenerator = transferIdGenerator;
    }

    public TransferStatusStorage getTransferStatusStorage() {
        return transferStatusStorage;
    }

    public void setTransferStatusStorage(TransferStatusStorage transferStatusStorage) {
        this.transferStatusStorage = transferStatusStorage;
    }

    public TransferHandler getTransferHandler() {
        return transferHandler;
    }

    public void setTransferHandler(TransferHandler transferHandler) {
        this.transferHandler = transferHandler;
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
}
