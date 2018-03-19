package com.lightcomp.ft.sender;

import java.util.Collection;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.exception.TransferException;

/**
 * Transfer request defines target data and implements transfer callbacks.
 */
public interface TransferRequest {

    /**
     * External request id, can be null.
     */
    String getRequestId();

    /**
     * Checksum type of {@link SourceFile} checksum.
     */
    ChecksumType getChecksumType();

    /**
     * Source items.
     */
    Collection<SourceItem> getSourceItems();

    /**
     * Transfer progress callback.
     */
    void onTransferProgress(TransferStatus status);

    /**
     * Transfer success callback.
     */
    void onTransferSuccess();

    /**
     * Transfer canceled callback.
     */
    void onTransferCanceled();

    /**
     * Transfer failed callback.
     */
    void onTransferFailed(TransferException cause);
}
