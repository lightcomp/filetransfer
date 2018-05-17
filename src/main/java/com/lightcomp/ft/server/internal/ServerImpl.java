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
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.DownloadHandler;
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

    private final Map<String, AbstractTransfer> transferIdMap = new HashMap<>();

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
        this.executor = new TaskExecutor(config.getThreadPoolSize());
        this.idGenerator = config.getTransferIdGenerator();
        this.handler = config.getTransferHandler();
        this.statusStorage = config.getTransferStatusStorage();
        // interval is 1/10 of inactive timeout or at minimum 1s
        this.wakeupInterval = Math.max(config.getInactiveTimeout() * 1000 / 10, 1000);
    }

    /* server methods */

    @Override
    public Object getImplementor() {
        return new FileTransferServiceImpl(this);
    }

    @Override
    public synchronized void start() {
        Validate.isTrue(state == State.INIT);

        state = State.RUNNING;
        // start manager thread
        Thread managerThread = new Thread(this::run, "FileTransfer_TransferManager");
        managerThread.start();
        // start shared executor
        executor.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // stop shared executor
            executor.stop();
            // notify manager thread about stopping
            notifyAll();
            // wait until manager thread terminates
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
        AbstractTransfer transfer;
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING);
            transfer = transferIdMap.get(transferId);
        }
        if (transfer == null) {
            throw new TransferExceptionBuilder("Transfer not found").addParam("transferId", transferId).build();
        }
        transfer.cancel();
    }

    @Override
    public TransferStatus getCurrentStatus(String transferId) {
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING);
            // try to get status from transfer
            AbstractTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                return transfer.getStatus();
            }
        }
        // get status from storage or return null
        return statusStorage.getTransferStatus(transferId);
    }

    /* transfer manager methods */

    @Override
    public synchronized Transfer getTransfer(String transferId) throws FileTransferException {
        checkServerState();
        checkNewTransfer(transferId);
        // we must return existing transfer or throw exception
        AbstractTransfer transfer = transferIdMap.get(transferId);
        if (transfer == null) {
            throw new ErrorBuilder("Transfer not found").addParam("transferId", transferId).buildEx();
        }
        return transfer;
    }

    @Override
    public TransferStatus getConfirmedStatus(String transferId) throws FileTransferException {
        synchronized (this) {
            checkServerState();
            checkNewTransfer(transferId);
            // try to get status from transfer
            AbstractTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                TransferStatusImpl ts = transfer.getStatus();
                // status must respect busy state
                if (ts.isBusy()) {
                    throw new ErrorBuilder("Transfer is busy", transfer).buildEx(ErrorCode.BUSY);
                }
                return ts;
            }
        }
        // get status from storage or throw exception
        TransferStatus ts = statusStorage.getTransferStatus(transferId);
        if (ts == null) {
            throw new ErrorBuilder("Transfer status not found").addParam("transferId", transferId).buildEx();
        }
        return ts;
    }

    @Override
    public String createTransferAsync(GenericDataType request) throws FileTransferException {
        String transferId = idGenerator.generateId();
        if (StringUtils.isEmpty(transferId)) {
            throw new ErrorBuilder("Id generator returned empty transfer id").buildEx();
        }
        synchronized (this) {
            checkServerState();
            // check running transfers and new transfers for duplicate id
            if (transferIdMap.containsKey(transferId) || newTransferIds.contains(transferId)) {
                throw new ErrorBuilder("Id generator generated duplicate transfer id").addParam("transferId", transferId)
                        .buildEx();
            }
            // add new transfer to lookup
            newTransferIds.add(transferId);
        }
        executor.addTask(() -> {
            try {
                TransferDataHandler dataHandler = handler.onTransferBegin(transferId, request);
                createTransfer(transferId, dataHandler);
            } catch (Throwable t) {
                transferCreationFailed(transferId, t);
            }
        });
        return transferId;
    }

    private void createTransfer(String transferId, TransferDataHandler dataHandler) throws TransferException {
        AbstractTransfer transfer;
        if (dataHandler.getMode().equals(Mode.UPLOAD)) {
            UploadHandler uh = (UploadHandler) dataHandler;
            transfer = new UploadTransfer(transferId, uh, config, executor);
        } else {
            DownloadHandler dh = (DownloadHandler) dataHandler;
            transfer = new DownloadTransfer(transferId, dh, config, executor);
        }
        transfer.init();
        // publish initialized transfer
        synchronized (this) {
            newTransferIds.remove(transferId);
            transferIdMap.put(transferId, transfer);
        }
    }

    private void transferCreationFailed(String transferId, Throwable cause) {
        ErrorBuilder eb = new ErrorBuilder("Failed to create transfer").addParam("transferId", transferId).setCause(cause);
        eb.log(logger);
        // create failed status for storage
        TransferStatusImpl status = new TransferStatusImpl();
        status.changeStateToFailed(eb.buildDesc());
        try {
            statusStorage.saveTransferStatus(transferId, status);
        } catch (Throwable t) {
            // log and ignore this exception, no easy recovery
            logger.error("FATAL: failed to store terminated new transfer", t);
        }
        synchronized (this) {
            newTransferIds.remove(transferId);
        }
    }

    /**
     * When server is not running then fatal exception is thrown. Caller must ensure synchronization.
     */
    private void checkServerState() throws FileTransferException {
        if (state != State.RUNNING) {
            throw new ErrorBuilder("Server is not running").buildEx();
        }
    }

    /**
     * When transfer is not yet created then busy exception is thrown. Caller must ensure synchronization.
     */
    private void checkNewTransfer(String transferId) throws FileTransferException {
        if (newTransferIds.contains(transferId)) {
            throw new ErrorBuilder("Transfer is busy").addParam("transferId", transferId).buildEx(ErrorCode.BUSY);
        }
    }

    /* async manager methods */

    private void run() {
        List<AbstractTransfer> currentTransfers;

        while (true) {
            synchronized (this) {
                // copy map values in sync block
                currentTransfers = new ArrayList<>(transferIdMap.values());
                // exit loop if not running
                if (state != State.RUNNING) {
                    break;
                }
            }
            // terminate all inactive transfer
            List<String> terminatedTransferIds = terminateInactiveTransfers(currentTransfers);

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
        terminateTransfers(currentTransfers);
        synchronized (this) {
            state = State.TERMINATED;
            // notify stopping threads
            notifyAll();
            // clear all transfers
            transferIdMap.clear();
        }
    }

    private List<String> terminateInactiveTransfers(Collection<AbstractTransfer> currentTransfers) {
        List<String> terminatedTransferIds = new ArrayList<>();
        for (AbstractTransfer transfer : currentTransfers) {
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

    private void terminateTransfers(Collection<AbstractTransfer> currentTransfers) {
        for (AbstractTransfer transfer : currentTransfers) {
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
