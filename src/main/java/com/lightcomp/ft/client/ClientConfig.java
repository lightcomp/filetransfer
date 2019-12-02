package com.lightcomp.ft.client;

import java.nio.file.Path;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.configuration.security.AuthorizationPolicy;

import com.lightcomp.ft.common.Checksum.Algorithm;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.send.SendConfig;

/**
 * Client configuration.
 */
public final class ClientConfig implements SendConfig {

    private final String address;

    private Path workDir = PathUtils.SYS_TEMP;

    private int threadPoolSize = 1;

    private int requestTimeout = 60;

    private int recoveryDelay = 60;

    private long maxFrameSize = 10 * 1024 * 1024;

    private int maxFrameBlocks = 10000;

    private Algorithm checksumAlg = Algorithm.SHA_512;

    private boolean soapLogging;
    
    private Authorization authorization;

    /**
     * @param address
     *            server address
     */
    public ClientConfig(String address) {
        this.address = Validate.notBlank(address);
    }

    /**
     * @return Server address.
     */
    public String getAddress() {
        return address;
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
     * @return Maximum of simultaneously running transfers.
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * @param threadPoolSize
     *            maximum of simultaneously running transfers, greater than zero
     */
    public void setThreadPoolSize(int threadPoolSize) {
        Validate.isTrue(threadPoolSize > 0);
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * @return Timeout for server request in seconds.
     */
    public int getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * @param requestTimeout
     *            timeout for server request in seconds, non negative
     */
    public void setRequestTimeout(int requestTimeout) {
        Validate.isTrue(requestTimeout >= 0);
        this.requestTimeout = requestTimeout;
    }

    /**
     * @return Delay before transfer recovery in seconds.
     */
    public int getRecoveryDelay() {
        return recoveryDelay;
    }

    /**
     * @param recoveryDelay
     *            delay before transfer recovery in seconds, non negative
     */
    public void setRecoveryDelay(int recoveryDelay) {
        Validate.isTrue(recoveryDelay >= 0);
        this.recoveryDelay = recoveryDelay;
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

    /**
     * @return When true any soap message will be logged.
     */
    public boolean isSoapLogging() {
        return soapLogging;
    }

    /**
     * @param soapLogging
     *            when true any soap messages will be logged
     */
    public void setSoapLogging(boolean soapLogging) {
        this.soapLogging = soapLogging;
    }
    
    public Authorization getAuthorization() {
		return authorization;
	}

	public void setAuthorization(Authorization authorization) {
		this.authorization = authorization;
	}

	public static class Authorization {

        private String username;

        private String password;

        private String authorization;

        private String authorizationType;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAuthorization() {
            return authorization;
        }

        public void setAuthorization(String authorization) {
            this.authorization = authorization;
        }

        public String getAuthorizationType() {
            return authorizationType;
        }

        public void setAuthorizationType(String authorizationType) {
            this.authorizationType = authorizationType;
        }

        public AuthorizationPolicy toPolicy() {
            AuthorizationPolicy policy = new AuthorizationPolicy();
            policy.setUserName(username);
            policy.setPassword(password);
            policy.setAuthorization(authorization);
            policy.setAuthorizationType(authorizationType);
            return policy;
        }
    }

    
}
