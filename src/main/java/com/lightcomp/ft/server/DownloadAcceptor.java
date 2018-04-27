package com.lightcomp.ft.server;

import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceItem;

public interface DownloadAcceptor extends TransferAcceptor {

    /**
     * Returns new source item iterator.
     */
    Iterator<SourceItem> getItemIterator();
}
