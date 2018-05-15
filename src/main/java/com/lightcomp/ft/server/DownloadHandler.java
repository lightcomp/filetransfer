package com.lightcomp.ft.server;

import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceItem;

/**
 * Handler for download transfer.
 */
public interface DownloadHandler extends TransferDataHandler {

    /**
     * Returns new iterator for root items, not-null.
     */
    Iterator<SourceItem> getItemIterator();
}
