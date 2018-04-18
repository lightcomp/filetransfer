package com.lightcomp.ft.server.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.server.internal.upload.UploadTransfer;

import cxf.FileTransferException;

public class ServerImpl implements Server, TransferProvider {

    /**
     * Internal server state.
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private static final Logger logger = LoggerFactory.getLogger(ServerImpl.class);

    private final Map<String, AbstractTransfer> transferIdMap = new HashMap<>();

    private final TransferReceiver receiver;

    private final ServerConfig config;

    private State state = State.INIT;

    public ServerImpl(TransferReceiver receiver, ServerConfig config) {
        this.receiver = receiver;
        this.config = config;
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
        Thread managerThread = new Thread(this::run, "FileTransfer_TransferManager");
        managerThread.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // notify manager thread about stopping
            notify();
            // wait until manager thread does not terminate
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

    /* provider methods */

    @Override
    public synchronized Transfer getTransfer(String transferId) throws FileTransferException {
        checkRunningState();

        AbstractTransfer transfer = transferIdMap.get(transferId);
        if (transfer == null) {
            throw FileTransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
        }
        return transfer;
    }

    @Override
    public Transfer createTransfer(String requestId) throws FileTransferException {
        checkRunningState();

        TransferAcceptor acceptor = receiver.onTransferBegin(requestId);
        // check if transfer was rejected
        if (acceptor == null) {
            throw FileTransferExceptionBuilder.from("Transfer rejected by receiver").addParam("requestId", requestId).build();
        }
        // further exceptions must be reported back to acceptor
        try {
            return createTransfer(requestId, acceptor);
        } catch (Throwable t) {
            acceptor.onTransferFailed(t);
            throw FileTransferExceptionBuilder.from(t.getMessage()).setCause(t.getCause()).build();
        }
    }

    private Transfer createTransfer(String requestId, TransferAcceptor acceptor) {
        String transferId = acceptor.getTransferId();
        // check empty transfer id
        if (StringUtils.isEmpty(transferId)) {
            throw TransferExceptionBuilder.from("Receiver supplied empty transfer id").addParam("requestId", requestId).build();
        }
        // create transfer
        AbstractTransfer transfer;
        if (acceptor.getMode().equals(Mode.UPLOAD)) {
            UploadAcceptor ua = (UploadAcceptor) acceptor;
            transfer = new UploadTransfer(ua, requestId, config);
        } else {
            // TODO: server download impl
            throw new UnsupportedOperationException();
        }
        // publish transfer
        synchronized (this) {
            if (state != State.RUNNING) {
                throw new IllegalStateException("Server is not running");
            }
            if (transferIdMap.putIfAbsent(transferId, transfer) == null) {
                return transfer;
            }
            throw TransferExceptionBuilder.from(transfer, "Receiver supplied duplicit transfer id").build();
        }
    }

    private void checkRunningState() throws FileTransferException {
        if (state != State.RUNNING) {
            throw FileTransferExceptionBuilder.from("Server is not running").build();
        }
    }

    /* manager methods */

    private void run() {
        while (true) {
            List<AbstractTransfer> inactiveRunningTransfers = new ArrayList<>();
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    // notify stopping threads
                    notifyAll();
                    // break thread loop
                    break;
                }
                processInactiveTransfers(inactiveRunningTransfers);
            }

            // try to cancel inactive running transfers
            cancelTransfers(inactiveRunningTransfers);

            synchronized (this) {
                try {
                    // wait for stopping notification or timeout
                    wait(1000);
                } catch (InterruptedException e) {
                    // service cannot run without manager
                    state = State.STOPPING;
                }
            }
        }
        // no one can access transferIdMap in terminated state thus thread-safe
        cancelTransfers(transferIdMap.values());
        transferIdMap.clear();
    }

    /**
     * Process all inactive transfers. Terminated transfers will be removed but
     * running transfers will be added to inactiveRunningTransfers, because they
     * must be canceled first. Caller must ensure synchronization.
     */
    private void processInactiveTransfers(Collection<AbstractTransfer> inactiveRunningTransfers) {
        Iterator<Entry<String, AbstractTransfer>> it = transferIdMap.entrySet().iterator();
        while (it.hasNext()) {
            AbstractTransfer transfer = it.next().getValue();
            ActivityStatus status = transfer.getActivityStatus();
            if (status == ActivityStatus.INACTIVE_RUNNING) {
                inactiveRunningTransfers.add(transfer);
            }
            if (status == ActivityStatus.INACTIVE_TERMINATED) {
                it.remove();
            }
        }
    }

    /**
     * Cancels specified transfers. Caller must ensure synchronization.
     */
    private void cancelTransfers(Collection<AbstractTransfer> transfers) {
        for (AbstractTransfer transfer : transfers) {
            try {
                transfer.cancel();
            } catch (Throwable t) {
                logger.warn("Unable to cancel transfer", t);
            }
        }
    }
}
