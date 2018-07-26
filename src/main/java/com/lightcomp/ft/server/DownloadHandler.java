package com.lightcomp.ft.server;

import com.lightcomp.ft.core.send.items.SourceItemReader;

/**
 * Handler for download transfer.
 */
public interface DownloadHandler extends TransferDataHandler {

	/**
	 * Returns reader for root items, not-null.
	 */
	SourceItemReader getRootItemsReader();
}
