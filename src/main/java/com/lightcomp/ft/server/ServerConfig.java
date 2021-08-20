package com.lightcomp.ft.server;

import java.nio.file.Path;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.Checksum.Algorithm;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.SimpleIdGenerator;
import com.lightcomp.ft.core.TransferIdGenerator;
import com.lightcomp.ft.core.send.SendConfig;

/**
 * Server configuration.
 */
public class ServerConfig implements SendConfig {

    private final TransferStatusStorage statusStorage;

    private final TransferHandler transferHandler;

    private TransferIdGenerator transferIdGenerator = new SimpleIdGenerator();

    private Path workDir = PathUtils.SYS_TEMP;

    private int threadPoolSize = 5;

    private int inactiveTimeout = 60 * 5;

    private long maxFrameSize = 10 * 1024 * 1024L;

    private int maxFrameBlocks = 10000;

    private Algorithm checksumAlg = Algorithm.SHA_512;
    
    private boolean soapLogging;

    public ServerConfig(TransferHandler transferHandler, TransferStatusStorage statusStorage) {
        this.transferHandler = Validate.notNull(transferHandler);
        this.statusStorage = Validate.notNull(statusStorage);
    }

    public TransferHandler getTransferHandler() {
        return transferHandler;
    }

    public TransferStatusStorage getStatusStorage() {
        return statusStorage;
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

    @Override
    public long getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * @param maxFrameSize
     *            maximum frame size in bytes, must be at least equal to checksum byte length
     */
    public void setMaxFrameSize(long maxFrameSize) {
        Validate.isTrue(maxFrameSize > checksumAlg.getByteLen());
        this.maxFrameSize = maxFrameSize;
    }

    @Override
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

    @Override
    public Algorithm getChecksumAlg() {
        return checksumAlg;
    }

    /**
     * @param checksumAlg
     *            checksum algorithm, not-null
     */
    public void setChecksumAlg(Algorithm checksumAlg) {
        this.checksumAlg = Validate.notNull(checksumAlg);
    }

    public boolean isSoapLogging() {
        return soapLogging;
    }

    public void setSoapLogging(boolean soapLogging) {
        this.soapLogging = soapLogging;
    }
}
