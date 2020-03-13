package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.xsd.v1.GenericDataType;

class TransferHandlerImpl implements TransferHandler {

    private final Path workDir;
    
    private final Set<String> downloadModes;
    
    private final Set<String> uploadModes; 

    public TransferHandlerImpl(Path workDir, Set<String> downloadModes, Set<String> uploadModes) {
        this.workDir = workDir;
        this.downloadModes = downloadModes;
        this.uploadModes = uploadModes;
    }

    @Override
    public synchronized TransferDataHandler onTransferBegin(String transferId, GenericDataType request) {
        Path transferDir = workDir.resolve(request.getId());
        String requestType = request.getType();
        if (uploadModes.contains(requestType)) {
            try {
                Files.createDirectory(transferDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new UploadHandlerImpl(transferId, request, transferDir);
        } else if (downloadModes.contains(requestType)) {
        	return new DwnldHandlerImpl(transferId, request, transferDir);
        } else {
        	throw new IllegalStateException("Unsupported type of transfer " + requestType);
        }
    }
}
