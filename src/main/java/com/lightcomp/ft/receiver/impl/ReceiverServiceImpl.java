package com.lightcomp.ft.receiver.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;

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

    private final Map<String, Transfer> transferIdMap = new HashMap<>();

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
                    // we cannot recover, finish termination
                }
            }
        }
    }

    @Override
    public void cancelTransfer(String transferId) {
        Transfer transfer;
        synchronized (this) {
            Validate.isTrue(state == State.RUNNING);
            transfer = transferIdMap.get(transferId);
            if (transfer == null) {
                throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).build();
            }
        }
        if (!transfer.cancel()) {
            throw TransferExceptionBuilder.from("Committed transfer cannot be aborted").setTransfer(transfer).build();
        }
    }

    /* transfer provider methods */

    @Override
    public synchronized Transfer getTransfer(String transferId) throws FileTransferException {
        checkReceiverRunning();

        Transfer tc = transferIdMap.get(transferId);
        if (tc == null) {
            throw TransferExceptionBuilder.from("Transfer not found").addParam("transferId", transferId).setCode(ErrorCode.FATAL)
                    .buildFault();
        }
        return tc;
    }

    @Override
    public Transfer createTransfer(String requestId, ChecksumType checksumType) throws FileTransferException {
        checkReceiverRunning();

        TransferAcceptor ta = beginTransferListener.onTransferBegin(requestId);
        try {
            synchronized (this) {
                checkReceiverRunning();
                Transfer tc = new Transfer(ta, requestId, checksumType);
                if (transferIdMap.putIfAbsent(tc.getTransferId(), tc) != null) {
                    throw TransferExceptionBuilder.from("Transfer id already exists").setTransfer(tc).setCode(ErrorCode.FATAL).buildFault();
                }
                return tc;
            }
        } catch (FileTransferException fte) {
            ta.onTransferFailed(fte);
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
            List<Transfer> transfers;
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    // notify stopping thread
                    notify();
                    break;
                }
                transfers = new ArrayList<>(transferIdMap.values());
            }
            // try abort all inactive transfers
            Iterator<Transfer> it = transfers.iterator();
            while (it.hasNext()) {
                Transfer t = it.next();
                if (!t.abortIfInactive()) {
                    it.remove();
                }
            }
            synchronized (this) {
                // remove all aborted transfers
                transfers.forEach(tc -> transferIdMap.remove(tc.getTransferId()));
                // wait for timeout or notify by stop()
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // NOP - continue
                }
            }
        }
        // clear all transfers after terminated
        // thread-safe, nobody else can touch transferIdMap at this state
        transferIdMap.values().forEach(Transfer::abort);
        transferIdMap.clear();
    }
}
