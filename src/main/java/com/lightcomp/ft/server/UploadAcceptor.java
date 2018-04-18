package com.lightcomp.ft.server;

import java.nio.file.Path;

public interface UploadAcceptor extends TransferAcceptor {

    /**
     * Target directory for uploaded items.
     */
    Path getUploadDir();
}
