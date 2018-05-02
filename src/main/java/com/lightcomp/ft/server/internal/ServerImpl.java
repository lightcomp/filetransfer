package com.lightcomp.ft.server.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferAcceptor.Mode;
import com.lightcomp.ft.server.TransferReceiver;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.TransferStatusStorage;
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public class ServerImpl implements Server, TransferManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerImpl.class);

    /**
     * Internal server state.
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private final Map<String, AbstractTransfer> transferIdMap = new HashMap<>();

    private final TransferStatusStorage statusStorage;

    private final TransferReceiver receiver;

    private final ServerConfig config;

    private State state = State.INIT;

    public ServerImpl(TransferReceiver receiver, ServerConfig config, TransferStatusStorage statusStorage) {
        this.receiver = receiver;
        this.config = config;
        this.statusStorage = statusStorage;
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
    public FileTransferStatus getStatus(String transferId) throws FileTransferException {
        TransferStatus ts = null;
        synchronized (this) {
            if (state != State.RUNNING) {
                throw new FileTransferException("Server is not running");
            }
            AbstractTransfer transfer = transferIdMap.get(transferId);
            if (transfer != null) {
                ts = transfer.getStatus();
            }
        }
        if (ts == null) {
            ts = statusStorage.getTransferStatus(transferId);
            if (ts == null) {
                throw FileTransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
            }
        }
        FileTransferStatus fts = new FileTransferStatus();
        fts.setLastFrameSeqNum(ts.getLastFrameSeqNum());
        fts.setState(TransferState.convert(ts.getState()));
        return fts;
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
    public AbstractTransfer createTransfer(String requestId) throws FileTransferException {
        if (state != State.RUNNING) {
            throw new FileTransferException("Server is not running");
        }
        TransferAcceptor acceptor = receiver.onTransferBegin(requestId);
        // check if rejected
        if (acceptor == null) {
            throw FileTransferExceptionBuilder.from("Transfer was rejected by server").addParam("requestId", requestId).build();
        }
        // initialize transfer
        try {
            AbstractTransfer transfer = createTransfer(requestId, acceptor);
            // publish transfer
            synchronized (this) {
                if (state != State.RUNNING) {
                    throw new TransferException("Server is not running");
                }
                String transferId = transfer.getTransferId();
                if (transferIdMap.putIfAbsent(transferId, transfer) == null) {
                    return transfer;
                }
                throw TransferExceptionBuilder.from("Duplicit transfer id", transfer).build();
            }
        } catch (Throwable t) {
            logger.error("Transfer initialization failed", t);
            acceptor.onTransferFailed(t);
            throw FileTransferExceptionBuilder.from("Transfer initialization failed").setCause(t).build();
        }
    }

    private AbstractTransfer createTransfer(String requestId, TransferAcceptor acceptor) {
        String transferId = acceptor.getTransferId();
        // check empty transfer id
        if (StringUtils.isEmpty(transferId)) {
            throw TransferExceptionBuilder.from("Acceptor supplied empty transfer id").addParam("requestId", requestId).build();
        }
        // create transfer
        if (acceptor.getMode().equals(Mode.UPLOAD)) {
            if (!(acceptor instanceof UploadAcceptor)) {
                throw TransferExceptionBuilder.from("UploadAcceptor implementation expected")
                        .addParam("givenImpl", acceptor.getClass()).addParam("requestId", requestId).build();
            }
            UploadTransfer transfer = new UploadTransfer((UploadAcceptor) acceptor, requestId, config);
            transfer.init();
            return transfer;
        }
        // TODO: server download impl
        throw new UnsupportedOperationException();
    }

    private void run() {
        while (true) {
            List<AbstractTransfer> inactiveTransfers;
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    // notify stopping threads
                    notifyAll();
                    // break thread loop
                    break;
                }
                // copy map values in sync block
                inactiveTransfers = new ArrayList<>(transferIdMap.values());
            }
            // terminate all inactive transfer and remove active from list
            inactiveTransfers.removeIf(t -> {
                if (t.terminateIfInactive()) {
                    statusStorage.saveTransferStatus(t.getTransferId(), t.getStatus());
                    return false;
                }
                return true;
            });
            synchronized (this) {
                // remove all terminated transfers
                inactiveTransfers.forEach(t -> transferIdMap.remove(t.getTransferId()));
                // wait for next run
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // service cannot run without manager
                    state = State.STOPPING;
                }
            }
        }
        // no one can access transferIdMap in terminated state thus thread-safe
        transferIdMap.values().forEach(AbstractTransfer::terminate);
        transferIdMap.clear();
    }
}
