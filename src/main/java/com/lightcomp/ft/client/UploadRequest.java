package com.lightcomp.ft.client;

import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceItem;

/**
 * Upload request.
 */
public interface UploadRequest extends TransferRequest {

    /**
     * Returns new iterator for root items, not-null.
     */
    Iterator<SourceItem> getItemIterator();
}