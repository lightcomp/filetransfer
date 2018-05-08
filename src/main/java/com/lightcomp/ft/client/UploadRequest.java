package com.lightcomp.ft.client;

import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceItem;

public interface UploadRequest extends TransferRequest {

    /**
     * Returns new source item iterator, not-null.
     */
    Iterator<SourceItem> getItemIterator();
}
