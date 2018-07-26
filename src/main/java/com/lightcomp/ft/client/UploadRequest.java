package com.lightcomp.ft.client;

import com.lightcomp.ft.core.send.items.SourceItemReader;

/**
 * Upload request.
 */
public interface UploadRequest extends TransferRequest {

	/**
	 * Returns reader for root items, not-null.
	 */
	SourceItemReader getRootItemsReader();
}