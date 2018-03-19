package com.lightcomp.ft.sender;

/**
 * Sender service dispatching all transfer requests.
 */
public interface SenderService {

    /**
     * Begin transfer for specified request.
     * 
     * @return Instance of transfer.
     */
    Transfer beginTransfer(TransferRequest transferRequest);

    /**
     * Start service.
     */
    void start();

    /**
     * Stop service.
     */
    void stop();
}
