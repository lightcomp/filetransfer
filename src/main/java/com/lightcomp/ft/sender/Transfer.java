package com.lightcomp.ft.sender;

import com.lightcomp.ft.exception.TransferException;

/**
 * Representation of one transfer.
 */
public interface Transfer {

    /**
     * Returns current transfer status.
     */
    TransferStatus getStatus();

    /**
     * Cancel transfer. Caller will wait for termination if the transfer is active.
     * When canceled {@link TransferRequest#onTransferCanceled()} is called.
     * 
     * @throws TransferException
     *             When transfer already committed or failed.
     */
    void cancel();
}
