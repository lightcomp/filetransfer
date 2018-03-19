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
     * Cancel transfer. Caller will wait if the transfer is processing commit. When
     * canceled {@link TransferRequest#onTransferCanceled()} is called.
     * 
     * @throws TransferException
     *             When transfer already committed.
     */
    void cancel();
}
