package com.lightcomp.ft.server.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferAcceptor.Mode;
import com.lightcomp.ft.server.TransferReceiver;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.TransferStatusStorage;
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.GenericData;

public class ServerImpl implements Server, TransferManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerImpl.class);

    /**
     * Internal server state.
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private final Map<String, AbstractTransfer> transferIdMap = new HashMap<>();

    private final ServerConfig config;

    private final TransferReceiver receiver;

    private final TransferStatusStorage statusStorage;

    private State state = State.INIT;

    public ServerImpl(ServerConfig config) {
        this.config = config;
        this.receiver = config.getReceiver();
        this.statusStorage = config.getStatusStorage();
    }

    @Override
    public Object getImplementor() {
        return new FileTransferServiceImpl(this);
    }

    @Override
    public synchronized void start() {
        Validate.isTrue(state == State.INIT);

        state = State.RUNNING;
        Thread managerThread = new Thread(this::run, "FileTransfer_TransferManager");
        managerThread.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // notify manager thread about stopping
            notify();
            // wait until manager thread terminates
            while (state != State.TERMINATED) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void cancelTransfer(String transferId) {
        AbstractTransfer transfer;
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING);
            transfer = transferIdMap.get(transferId);
        }
        if (transfer == null) {
            throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
        }
        transfer.cancel();
    }

    @Override
    public TransferStatus getTransferStatus(String transferId) {
        synchronized (this) {
            AbstractTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                return transfer.getStatus();
            }
        }
        return statusStorage.getTransferStatus(transferId);
    }

    @Override
    public FileTransferStatus getFileTransferStatus(String transferId) throws FileTransferException {
        synchronized (this) {
            AbstractTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                TransferStatusImpl ts = transfer.getStatus();
                if (ts.isBusy()) {
                    throw FileTransferExceptionBuilder.from("Transfer is busy", transfer.getAcceptor()).setCode(ErrorCode.BUSY)
                            .build();
                }
                return createFileTransferStatus(ts);
            }
        }
        TransferStatus ts = statusStorage.getTransferStatus(transferId);
        if (ts == null) {
            throw FileTransferExceptionBuilder.from("Transfer status not found").addParam("transferId", transferId).build();
        }
        return createFileTransferStatus(ts);
    }

    @Override
    public synchronized AbstractTransfer getTransfer(String transferId) throws FileTransferException {
        if (state != State.RUNNING) {
            throw new FileTransferException("Server is not running");
        }
        AbstractTransfer transfer = transferIdMap.get(transferId);
        if (transfer == null) {
            throw FileTransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
        }
        return transfer;
    }

    @Override
    public AbstractTransfer createTransfer(GenericData request) throws FileTransferException {
        // we cannot sync yet because of receiver callback
        if (state != State.RUNNING) {
            throw new FileTransferException("Server is not running");
        }
        TransferAcceptor acceptor = receiver.onTransferBegin(request);
        // check if rejected
        if (acceptor == null) {
            throw new FileTransferException("Transfer was rejected by server");
        }
        // initialize transfer
        synchronized (this) {
            if (state != State.RUNNING) {
                throw new FileTransferException("Server is not running");
            }
            try {
                AbstractTransfer transfer = createTransfer(acceptor);
                if (transferIdMap.putIfAbsent(transfer.getTransferId(), transfer) != null) {
                    throw new IllegalStateException("Transfer id supplied by acceptor already exists");
                }
                return transfer;
            } catch (Throwable t) {
                String msg = TransferExceptionBuilder.from("Failed to create transfer", acceptor).buildMsg();
                logger.error(msg, t);
                acceptor.onTransferFailed(t);
                throw FileTransferExceptionBuilder.from(msg, acceptor).setCause(t).build();
            }
        }
    }

    private AbstractTransfer createTransfer(TransferAcceptor acceptor) {
        String transferId = acceptor.getTransferId();
        // check empty transfer id
        if (StringUtils.isEmpty(transferId)) {
            throw new IllegalArgumentException("Acceptor with empty transfer id");
        }
        // create upload transfer
        if (acceptor.getMode().equals(Mode.UPLOAD)) {
            UploadAcceptor uploadAcceptor = (UploadAcceptor) acceptor;
            UploadTransfer transfer = new UploadTransfer(uploadAcceptor, config.getInactiveTimeout());
            transfer.init();
            return transfer;
        }
        // TODO: create download transfer
        throw new UnsupportedOperationException();
    }

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
                    wait(1000);
                } catch (InterruptedException e) {
                    // service cannot run without manager
                    state = State.STOPPING;
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
                    logger.error("FATAL: failed to store terminated transfer", t);
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

    public static FileTransferStatus createFileTransferStatus(TransferStatus ts) {
        FileTransferState state = ts.getState().toExternal();
        if (state == null) {
            throw new IllegalStateException("Failed to convert internal state");
        }
        FileTransferStatus fts = new FileTransferStatus();
        fts.setLastFrameSeqNum(ts.getLastFrameSeqNum());
        fts.setState(state);
        return fts;
    }
}
