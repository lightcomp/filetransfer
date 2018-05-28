package com.lightcomp.ft.core.send;

/**
 * This callback is used when outgoing MTOM data stream throws exception. Default behavior of MTOM
 * impl. only closes the stream and does not fire exception.
 */
public interface DataSendFailureCallback {

    /**
     * Called when outgoing MTOM data stream throws exception.
     */
    void onDataSendFailed(Throwable cause);
}
