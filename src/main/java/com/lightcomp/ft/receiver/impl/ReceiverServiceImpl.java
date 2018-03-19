package com.lightcomp.ft.receiver.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.receiver.BeginTransferListener;
import com.lightcomp.ft.receiver.ReceiverService;
import com.lightcomp.ft.receiver.TransferAcceptor;
import com.lightcomp.ft.xsd.v1.ErrorCode;

import cxf.FileTransferException;

public class ReceiverServiceImpl implements ReceiverService, TransferProvider {

    /**
     * Internal state. Do not change definition order!
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private static final Logger logger = LoggerFactory.getLogger(ReceiverServiceImpl.class);

    private final Map<String, TransferImpl> transferIdMap = new HashMap<>();

    private final BeginTransferListener beginTransferListener;

    private Thread managerThread;

    private State state = State.INIT;

    public ReceiverServiceImpl(BeginTransferListener beginTransferListener) {
        this.beginTransferListener = beginTransferListener;
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
        TransferImpl t;
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING);
            t = transferIdMap.get(transferId);
        }
        if (t == null) {
            throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
        }
        t.cancel();
    }

    /* transfer provider methods */

    @Override
    public synchronized TransferImpl getTransfer(String transferId) throws FileTransferException {
        checkReceiverRunning();

        TransferImpl t = transferIdMap.get(transferId);
        if (t == null) {
            throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).setCode(ErrorCode.FATAL)
                    .buildFault();
        }
        return t;
    }

    @Override
    public TransferImpl createTransfer(String requestId, ChecksumType checksumType) throws FileTransferException {
        checkReceiverRunning();

        TransferAcceptor acceptor = beginTransferListener.onTransferBegin(requestId);
        try {
            synchronized (this) {
                checkReceiverRunning();
                TransferImpl t = new TransferImpl(acceptor, requestId, checksumType);
                if (transferIdMap.putIfAbsent(t.getTransferId(), t) != null) {
                    throw TransferExceptionBuilder.from("Transfer id already exists").setTransfer(t).setCode(ErrorCode.FATAL).buildFault();
                }
                return t;
            }
        } catch (FileTransferException fte) {
            acceptor.onTransferFailed(fte);
            throw fte;
        }
    }

    private synchronized void checkReceiverRunning() throws FileTransferException {
        if (state != State.RUNNING) {
            throw TransferExceptionBuilder.from("Receiver service is not running").setCode(ErrorCode.FATAL).buildFault();
        }
    }

    /* manager methods */

    private void run() {
        while (true) {
            List<TransferImpl> transfers;
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    // notify stopping thread
                    notify();
                    // exit manager thread loop
                    break;
                }
                transfers = new LinkedList<>(transferIdMap.values());
            }
            // try cancel all inactive transfers
            Iterator<TransferImpl> it = transfers.iterator();
            while (it.hasNext()) {
                TransferImpl t = it.next();
                if (!t.cancelIfInactive()) {
                    it.remove();
                }
            }
            synchronized (this) {
                // remove all canceled transfers
                transfers.forEach(t -> transferIdMap.remove(t.getTransferId()));
                // wait for timeout or notify by stopping thread
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // with respect to the interruption, we have to end the service
                    state = State.TERMINATED;
                    break;
                }
            }
        }
        // no one can access transferIdMap in terminated state thus thread-safe
        cleanTransfers();
    }

    /**
     * Clear all transfers, each remaining transfer is aborted first. Method must be
     * synchronized by caller.
     */
    private void cleanTransfers() {
        for (TransferImpl transfer : transferIdMap.values()) {
            try {
                transfer.cancel();
            } catch (Throwable t) {
                logger.warn("Unable to cancel transfer after service termination", t);
            }
        }
        transferIdMap.clear();
    }
}
