package com.lightcomp.ft.server.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.TransferIdGenerator;
import com.lightcomp.ft.exception.TransferExBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.EndpointFactory;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferDataHandler.Mode;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.TransferStatusStorage;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class ServerImpl implements Server, TransferManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerImpl.class);

    /**
     * Internal server state.
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private final Map<String, ServerTransfer> transferIdMap = new HashMap<>();

    private final Set<String> newTransferIds = new HashSet<>();

    private final ServerConfig config;

    private final TaskExecutor executor;

    private final TransferIdGenerator idGenerator;

    private final TransferHandler handler;

    private final TransferStatusStorage statusStorage;

    private final long wakeupInterval;

    private State state = State.INIT;

    public ServerImpl(ServerConfig config) {
        this.config = config;
        this.executor = new TaskExecutor(config.getThreadPoolSize(), "server");
        this.idGenerator = config.getTransferIdGenerator();
        this.handler = config.getTransferHandler();
        this.statusStorage = config.getStatusStorage();
        // interval is 1/10 of inactive timeout or at minimum 1s
        this.wakeupInterval = Math.max(config.getInactiveTimeout() * 1000 / 10, 1000);
    }

    /* server methods */

    @Override
    public EndpointFactory getEndpointFactory() {
    	return new EndpointFactory(this, config);
    }

    @Override
    public synchronized void start() {
        Validate.isTrue(state == State.INIT);

        state = State.RUNNING;
        // start server thread
        Thread serverThread = new Thread(this::run, "FileTransferServer");
        serverThread.start();
        // start shared executor
        executor.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // stop shared executor
            executor.stop();
            // notify server thread
            notifyAll();
            // wait until server thread terminates
            while (state != State.TERMINATED) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void cancelTransfer(String transferId) throws TransferException {
        ServerTransfer transfer;
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING || state == State.STOPPING);
            transfer = transferIdMap.get(transferId);
        }
        if (transfer == null) {
            throw new TransferExBuilder("Transfer not found").addParam("transferId", transferId).build();
        }
        transfer.cancel();
    }

    @Override
    public TransferStatus getTransferStatus(String transferId) {
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING || state == State.STOPPING);
            // get status from current transfer
            ServerTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                return transfer.getStatus();
            }
        }
        // transfer not found -> get status from storage
        return statusStorage.getTransferStatus(transferId);
    }

    /* transfer manager methods */

    @Override
    public Transfer getTransfer(String transferId) throws FileTransferException {
        synchronized (this) {
            checkServerState();
            // check if requested transfer is creating
            if (newTransferIds.contains(transferId)) {
                throw new ServerError("Transfer is busy").addParam("transferId", transferId).setCode(ErrorCode.BUSY)
                        .createEx();
            }
            // get current transfer
            ServerTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                return transfer;
            }
        }
        // transfer not found -> get status from storage
        TransferStatus ts = statusStorage.getTransferStatus(transferId);
        if (ts != null) {
            return new TerminatedTransfer(transferId, ts);
        }
        throw new ServerError("Transfer not found").addParam("transferId", transferId).createEx();
    }

    @Override
    public String createTransferAsync(GenericDataType request) throws FileTransferException {
        String transferId = idGenerator.generateId();
        if (StringUtils.isEmpty(transferId)) {
            throw new ServerError("Id generator returned empty transfer id").createEx();
        }
        synchronized (this) {
            checkServerState();
            // check running transfers and new transfers for duplicate id
            if (transferIdMap.containsKey(transferId) || newTransferIds.contains(transferId)) {
                throw new ServerError("Id generator generated duplicate transfer id").addParam("transferId", transferId)
                        .createEx();
            }
            // add new transfer to lookup
            newTransferIds.add(transferId);
        }
        executor.addTask(() -> {
            TransferDataHandler dataHandler = null;
            try {
                dataHandler = handler.onTransferBegin(transferId, request);
                createTransfer(transferId, dataHandler);
            } catch (Throwable t) {
                transferCreationFailed(transferId, dataHandler, t);
            }
        });
        return transferId;
    }

    private void createTransfer(String transferId, TransferDataHandler dataHandler) throws TransferException {
        ServerTransfer transfer;
        if (dataHandler.getMode().equals(Mode.UPLOAD)) {
            UploadHandler uh = (UploadHandler) dataHandler;
            transfer = new UploadTransfer(transferId, uh, config, executor);
        } else {
            DownloadHandler dh = (DownloadHandler) dataHandler;
            transfer = new DwnldTransfer(transferId, dh, config, executor);
        }
        // initialize transfer during async creation
        transfer.init();
        // publish initialized transfer
        synchronized (this) {
            newTransferIds.remove(transferId);
            transferIdMap.put(transferId, transfer);
        }
    }

    private void transferCreationFailed(String transferId, TransferDataHandler dataHandler, Throwable cause) {
        ServerError err = new ServerError("Failed to create transfer").addParam("transferId", transferId)
                .setCause(cause);
        err.log(logger);
        // save failed status to storage
        TransferStatusImpl status = new TransferStatusImpl();
        status.changeStateToFailed(err.getDesc());
        try {
            statusStorage.saveTransferStatus(transferId, status);
        } catch (Throwable t) {
            // log and ignore this exception, no easy recovery
            logger.error("FATAL: failed to store terminated new transfer", t);
        }
        // remove new transfer after storage save
        synchronized (this) {
            newTransferIds.remove(transferId);
        }
        // report fail to data handler if exists
        if (dataHandler != null) {
            try {
                dataHandler.onTransferFailed(err.getDesc());
            } catch (Throwable t) {
                err = new ServerError("Fail callback of data handler cause exception")
                        .addParam("transferId", transferId).setCause(t);
                err.log(logger);
            }
        }
    }

    /**
     * When server is not running then fatal exception is thrown. Caller must ensure synchronization.
     */
    private void checkServerState() throws FileTransferException {
        if (state != State.RUNNING) {
            throw new ServerError("Server is not running").createEx();
        }
    }

    /* async server methods */

    private void run() {
        List<ServerTransfer> currTransfers;
        while (true) {
            synchronized (this) {
                // copy map values in sync block
                currTransfers = new ArrayList<>(transferIdMap.values());
                // exit loop if not running
                if (state != State.RUNNING) {
                    break;
                }
            }
            // terminate all inactive transfer
            List<String> terminatedTransferIds = terminateInactiveTransfers(currTransfers);

            synchronized (this) {
                // remove all terminated transfers
                terminatedTransferIds.forEach(transferIdMap::remove);
                // wait for next run
                try {
                    wait(wakeupInterval);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        terminateTransfers(currTransfers);
        synchronized (this) {
            state = State.TERMINATED;
            // notify stopping threads
            notifyAll();
            // clear all transfers
            transferIdMap.clear();
        }
    }

    private List<String> terminateInactiveTransfers(Collection<ServerTransfer> currTransfers) {
        List<String> terminatedTransferIds = new ArrayList<>();
        for (ServerTransfer transfer : currTransfers) {
            if (transfer.terminateIfInactive()) {
                try {
                    statusStorage.saveTransferStatus(transfer.getTransferId(), transfer.getStatus());
                    terminatedTransferIds.add(transfer.getTransferId());
                } catch (Throwable t) {
                    // log and ignore this exception, no easy recovery
                    logger.error("FATAL: failed to store terminated inactive transfer", t);
                }
            }
        }
        return terminatedTransferIds;
    }

    private void terminateTransfers(Collection<ServerTransfer> currTransfers) {
        for (ServerTransfer transfer : currTransfers) {
            transfer.terminate();
            try {
                statusStorage.saveTransferStatus(transfer.getTransferId(), transfer.getStatus());
            } catch (Throwable t) {
                // log and ignore this exception, no easy recovery
                logger.error("FATAL: failed to store terminated transfer", t);
            }
        }
    }
}
