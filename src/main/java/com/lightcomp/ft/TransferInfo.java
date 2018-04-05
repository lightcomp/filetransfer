package com.lightcomp.ft;

/**
 * Base transfer description.
 */
public interface TransferInfo {

    String getTransferId();

    String getRequestId();

    /**
     * @return Returns true if cancel is pending.
     */
    boolean isCancelRequested();
}
