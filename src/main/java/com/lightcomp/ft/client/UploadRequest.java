package com.lightcomp.ft.client;

import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.xsd.v1.GenericData;

public interface UploadRequest extends TransferRequest {

    /**
     * Returns new source item iterator, not-null.
     */
    Iterator<SourceItem> getItemIterator();
    
    /**
     * Transfer success callback.
     */
    void onTransferSuccess(GenericData response);
}
