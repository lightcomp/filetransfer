package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.BeginResponse;
import com.lightcomp.ft.xsd.v1.GenericDataType;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class BeginOperation extends AbstractOperation {

    private final GenericDataType request;

    private String transferId;

    public BeginOperation(OperationHandler handler, FileTransferService service, GenericDataType request) {
        super(handler, service);
        this.request = request;
    }

    public String getTransferId() {
        return transferId;
    }

    @Override
    protected OperationStatus resolveServerStatus(TransferStatus status) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void send() throws FileTransferException {
        BeginResponse br = service.begin(request);
        transferId = br.getTransferId();
    }

    @Override
    protected boolean isRecoverable(ExceptionType type) {
        return false;
    }

    @Override
    protected OperationStatus recoveryFailed(Type type, Throwable ex, ExceptionType exType) {
        return super.recoveryFailed(type, ex, exType).setFailureMessage("Failed to begin transfer");
    }
}
