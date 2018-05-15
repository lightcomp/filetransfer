package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.ErrorBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.GenericData;

public abstract class AbstractTransfer implements Transfer, TransferInfo {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final String transferId;

    protected final TransferDataHandler handler;

    protected final ServerConfig config;

    protected final TaskExecutor executor;

    protected AbstractTransfer(String transferId, TransferDataHandler handler, ServerConfig config, TaskExecutor executor) {
        this.transferId = transferId;
        this.handler = handler;
        this.config = config;
        this.executor = executor;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public String getRequestId() {
        return handler.getRequestId();
    }

    /**
     * Transfer busy state. <i>Impl note: specialization should override this method.</i>
     */
    public synchronized boolean isBusy() {
        return status.getState() == TransferState.FINISHING;
    }

    /**
     * Returns current status, method must return status impl to allow read busy state.
     */
    public synchronized TransferStatusImpl getStatus() {
        TransferStatusImpl ts = status.copy();
        // busy is set only to this copy because only this method reveals status impl
        ts.setBusy(isBusy());
        return ts;
    }

    /**
     * Initializes transfer. If succeeds then transfer is able to send/receive data. Status change when fails is not needed
     * because the server never publishes an uninitialized transfer.
     */
    public void init() throws TransferException {
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
    }

    protected abstract void checkPreparedFinish() throws FileTransferException;

    @Override
    public GenericData finish() throws FileTransferException {
        ErrorBuilder eb = null;
        // check transfer state
        synchronized (this) {
            checkActiveTransfer();
            checkPreparedFinish();
            // check transfered frame
            if (status.getState() != TransferState.TRANSFERED) {
                eb = new ErrorBuilder("Unable to finish transfer in current state").setTransfer(this).addParam("currentState",
                        status.getState());
                // state must be changed in same sync block as check
                status.changeStateToFailed(eb.buildDesc());
                // notify canceling threads
                notifyAll();
            } else {
                status.changeState(TransferState.FINISHING);
            }
        }
        // transfer failed must be called outside sync block
        if (eb != null) {
            transferFailed(eb);
            throw eb.buildApiEx();
        }
        return createResponse();
    }

    /**
     * Creates response, if succeeds transfer will be finished.
     */
    private GenericData createResponse() throws FileTransferException {
        GenericData response;
        try {
            response = handler.onTransferSuccess();
        } catch (Throwable t) {
            ErrorBuilder eb = new ErrorBuilder("Success callback of data handler cause exception").setTransfer(this).setCause(t);
            transferFailed(eb);
            throw eb.buildApiEx();
        }
        synchronized (this) {
            // transfer can fail during finishing only when callback throws exception
            status.changeStateToFinished(response);
            // notify canceling threads
            notifyAll();
        }
        return response;
    }

    /**
     * Handles transfer failure. If transfer is already terminated error is properly logged.
     */
    void transferFailed(ErrorBuilder eb) {
        ErrorDesc desc = eb.buildDesc();
        synchronized (this) {
            if (status.getState().isTerminal()) {
                desc = status.getErrorDesc();
            } else {
                status.changeStateToFailed(desc);
                // notify canceling threads
                notifyAll();
            }
        }
        // report error if specified builder is cause of failure
        if (eb.isEqualDesc(desc)) {
            handler.onTransferFailed(desc);
            eb.log(logger);
        } else {
            eb.log(logger, "Terminated trasfer thrown exception");
        }
    }

    @Override
    public synchronized void abort() throws FileTransferException {
        synchronized (this) {
            while (true) {
                TransferState ts = status.getState();
                if (ts == TransferState.FINISHED) {
                    throw new ErrorBuilder("Finished transfer cannot be aborted").setTransfer(this).buildApiEx();
                }
                if (ts == TransferState.CANCELED || ts == TransferState.FAILED) {
                    return; // failed will be handled as canceled
                }
                if (ts == TransferState.FINISHING) {
                    try {
                        wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ErrorBuilder("Thread was interupted while waiting for abort").setTransfer(this).setCause(e)
                                .buildApiEx();
                    }
                    continue;
                }
                status.changeState(TransferState.CANCELED);
                // notify other canceling threads
                notifyAll();
                break;
            }
        }
        handler.onTransferCanceled();
    }

    public void cancel() throws TransferException {
        synchronized (this) {
            while (true) {
                TransferState ts = status.getState();
                if (ts == TransferState.FINISHED) {
                    throw new ErrorBuilder("Finished transfer cannot be canceled").setTransfer(this).buildEx();
                }
                if (ts == TransferState.FAILED) {
                    throw new ErrorBuilder("Failed transfer cannot be canceled").setTransfer(this).buildEx();
                }
                if (ts == TransferState.CANCELED) {
                    return;
                }
                if (ts == TransferState.FINISHING) {
                    try {
                        wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ErrorBuilder("Thread was interupted while waiting for cancel").setTransfer(this).setCause(e)
                                .buildEx();
                    }
                    continue;
                }
                status.changeState(TransferState.CANCELED);
                // notify other canceling threads
                notifyAll();
                break;
            }
        }
    }

    public void terminate() {
        boolean canceled = false;
        synchronized (this) {
            if (!status.getState().isTerminal()) {
                status.changeState(TransferState.CANCELED);
                canceled = true;
                // notify canceling threads
                notifyAll();
            }
        }
        if (canceled) {
            try {
                handler.onTransferCanceled();
            } catch (Throwable t) {
                // exception is only logged
                new ErrorBuilder("Cancel callback of data handler cause exception").setTransfer(this).setCause(t).log(logger);
            }
        }
        clearResources();
    }

    public boolean terminateIfInactive() {
        ErrorDesc errorDesc = null;
        synchronized (this) {
            if (!status.getState().isTerminal()) {
                // test for inactive transfer
                long timeout = config.getInactiveTimeout();
                LocalDateTime timeoutLimit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
                LocalDateTime lastActivity = status.getLastActivity();
                if (timeoutLimit.isBefore(lastActivity)) {
                    return false; // transfer still active
                }
                // transfer inactive
                errorDesc = new ErrorBuilder("Inactivity timeout reached").setTransfer(this).buildDesc();
                status.changeStateToFailed(errorDesc);
                // notify canceling threads
                notifyAll();
            }
        }
        if (errorDesc != null) {
            try {
                handler.onTransferFailed(errorDesc);
            } catch (Throwable t) {
                // exception is only logged
                new ErrorBuilder("Cancel callback of data handler cause exception").setTransfer(this).setCause(t).log(logger);
            }
        }
        clearResources();
        return true;
    }

    protected final void checkActiveTransfer() throws FileTransferException {
        switch (status.getState()) {
            case FINISHING:
                throw new ErrorBuilder("Transfer is finishing").setTransfer(this).buildApiEx(ErrorCode.BUSY);
            case FINISHED:
                throw new ErrorBuilder("Transfer finished").setTransfer(this).buildApiEx();
            case CANCELED:
                throw new ErrorBuilder("Transfer canceled").setTransfer(this).buildApiEx();
            case FAILED:
                throw ErrorBuilder.buildApiEx(status.getErrorDesc());
            default:
                return; // active transfer
        }
    }

    protected abstract void clearResources();
}
