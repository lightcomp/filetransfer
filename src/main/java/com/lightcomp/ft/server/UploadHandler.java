package com.lightcomp.ft.server;

import java.nio.file.Path;

public interface UploadHandler extends TransferDataHandler {

    /**
     * Target directory for uploaded items, not-null.
     */
    Path getUploadDir();
}
