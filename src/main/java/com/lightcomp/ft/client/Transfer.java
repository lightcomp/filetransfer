package com.lightcomp.ft.client;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;

/**
 * Client transfer.
 */
public interface Transfer extends TransferInfo {

    /**
     * Current transfer status.
     */
    TransferStatus getStatus();

    /**
     * Cancel transfer. Caller will wait for termination if the transfer is active. When canceled
     * {@link TransferRequest#onTransferCanceled()} is called.
     * 
     * @throws TransferException
     *             When transfer is finished or failed.
     */
    void cancel() throws TransferException;
}
