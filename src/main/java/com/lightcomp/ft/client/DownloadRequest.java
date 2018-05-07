package com.lightcomp.ft.client;

import java.nio.file.Path;

public interface DownloadRequest extends TransferRequest {

    /**
     * Target directory for downloaded items, not-null.
     */
    Path getDownloadDir();
    
    /**
     * Transfer success callback.
     */
    void onTransferSuccess();
}
