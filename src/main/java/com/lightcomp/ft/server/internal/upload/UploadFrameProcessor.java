package com.lightcomp.ft.server.internal.upload;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.receiver.CancelableTransfer;

public class UploadFrameProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UploadFrameProcessor.class);

    private final CancelableTransfer transfer;

    private InputStream is;

    public UploadFrameProcessor(CancelableTransfer transfer) {
        this.transfer = transfer;
    }

    
}
