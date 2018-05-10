package com.lightcomp.ft.server;

/**
 * Interface for storing and accessing final TransferStatus
 * 
 */
public interface TransferStatusStorage {

	/**
	 * Save finished transfer
	 * @param transferId
	 * @param status
	 */
    void saveTransferStatus(String transferId, TransferStatus status);
    
    /**
     * Return status of finished transfer
     * @param transferId
     * @return
     *  Return transfer status for given trasferId. 
     * 	Return null if such transferId does not exist.
     *  
     *  Server status have to be returned with all data including optional result. 
     */
    TransferStatus getTransferStatus(String transferId);
}
