package com.lightcomp.ft.client;

import java.util.Iterator;

public interface UploadRequest extends TransferRequest {

    /**
     * Returns new source item iterator, not-null.
     */
    Iterator<SourceItem> getItemIterator();
}
