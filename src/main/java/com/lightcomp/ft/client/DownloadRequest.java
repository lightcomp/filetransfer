package com.lightcomp.ft.client;

import java.nio.file.Path;

/**
 * Download request.
 */
public interface DownloadRequest extends TransferRequest {

    /**
     * Target directory for downloaded items, not-null.
     */
    Path getDownloadDir();
}
