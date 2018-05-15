package com.lightcomp.ft.server;

/**
 * Storage used for storing and accessing status of terminated transfers.
 */
public interface TransferStatusStorage {

    /**
     * Save terminated transfer
     * 
     * @param transferId
     * @param status
     */
    void saveTransferStatus(String transferId, TransferStatus status);

    /**
     * Return status of terminated transfer
     * 
     * @param transferId
     * @return Return transfer status for given trasferId. Returns null if such transferId does not exist.
     * 
     *         Server status have to be returned with all data including optional result.
     */
    TransferStatus getTransferStatus(String transferId);
}
