package com.lightcomp.ft.receiver.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.receiver.BeginTransferListener;
import com.lightcomp.ft.receiver.ReceiverConfig;
import com.lightcomp.ft.receiver.ReceiverService;
import com.lightcomp.ft.receiver.TransferAcceptor;
import com.lightcomp.ft.receiver.impl.TransferImpl.ActivityStatus;
import com.lightcomp.ft.xsd.v1.ErrorCode;

import cxf.FileTransferException;

public class ReceiverServiceImpl implements ReceiverService, TransferProvider {

    /**
     * Internal receiver service state.
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private static final Logger logger = LoggerFactory.getLogger(ReceiverServiceImpl.class);

    private final Map<String, TransferImpl> transferIdMap = new HashMap<>();

    private final BeginTransferListener beginTransferListener;

    private final ReceiverConfig config;

    private Thread managerThread;

    private State state = State.INIT;

    public ReceiverServiceImpl(BeginTransferListener beginTransferListener, ReceiverConfig config) {
        this.beginTransferListener = beginTransferListener;
        this.config = config;
    }

    /* service methods */

    @Override
    public Object getImplementor() {
        return new FileTransferServiceImpl(this);
    }

    @Override
    public synchronized void start() {
        Validate.isTrue(state == State.INIT);

        state = State.RUNNING;
        managerThread = new Thread(this::run, "ReceiverServiceManager");
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
        TransferImpl transfer;
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING);
            transfer = transferIdMap.get(transferId);
        }
        if (transfer == null) {
            throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
        }
        transfer.cancel();
    }

    /* transfer provider methods */

    @Override
    public synchronized TransferImpl getTransfer(String transferId) throws FileTransferException {
        checkRunningState();

        TransferImpl transfer = transferIdMap.get(transferId);
        if (transfer == null) {
            throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).setCode(ErrorCode.FATAL)
                    .buildFault();
        }
        return transfer;
    }

    @Override
    public TransferImpl createTransfer(String requestId) throws FileTransferException {
        checkRunningState();

        TransferAcceptor acceptor = beginTransferListener.onTransferBegin(requestId);
        String transferId = Validate.notEmpty(acceptor.getTransferId());
        try {
            synchronized (this) {
                checkRunningState();
                TransferImpl transfer = new TransferImpl(acceptor, requestId, config);
                if (transferIdMap.putIfAbsent(transferId, transfer) != null) {
                    throw TransferExceptionBuilder.from("Transfer id already exists").setTransfer(transfer)
                            .setCode(ErrorCode.FATAL).buildFault();
                }
                return transfer;
            }
        } catch (FileTransferException fte) {
            acceptor.onTransferFailed(fte);
            throw fte;
        }
    }

    /**
     * Checks if the service is running otherwise exception is thrown. Caller
     * ensures synchronization if needed.
     */
    private void checkRunningState() throws FileTransferException {
        if (state != State.RUNNING) {
            throw TransferExceptionBuilder.from("Receiver service is not running").setCode(ErrorCode.FATAL).buildFault();
        }
    }

    /* manager methods */

    private void run() {
        while (true) {
            List<TransferImpl> timeoutedTransfers = new ArrayList<>();
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    // notify stopping threads
                    notifyAll();
                    // break thread loop
                    break;
                }
                removeInactiveTransfers(timeoutedTransfers);
            }

            // try to cancel timed out transfers
            cancelTransfers(timeoutedTransfers);

            synchronized (this) {
                try {
                    // wait for timeout or stopping notification
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
     * Removes all terminated transfers. Also adds timeouted transfers to specified
     * collection. Caller must ensure synchronization.
     */
    private void removeInactiveTransfers(Collection<TransferImpl> timeoutedTransfers) {
        Iterator<Entry<String, TransferImpl>> it = transferIdMap.entrySet().iterator();
        while (it.hasNext()) {
            TransferImpl transfer = it.next().getValue();
            ActivityStatus status = transfer.getActivityStatus();
            if (status == ActivityStatus.TIMEOUT_TERMINATED) {
                it.remove();
            }
            if (status == ActivityStatus.TIMEOUT) {
                timeoutedTransfers.add(transfer);
            }
        }
    }

    /**
     * Cancels specified transfers. Caller must ensure synchronization.
     */
    private void cancelTransfers(Collection<TransferImpl> transfers) {
        for (TransferImpl transfer : transfers) {
            try {
                transfer.cancel();
            } catch (Throwable t) {
                logger.warn("Unable to cancel transfer", t);
            }
        }
    }
}
