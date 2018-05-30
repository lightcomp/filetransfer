package com.lightcomp.ft.client.internal.operations;

import org.apache.commons.lang3.StringUtils;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.BeginResponse;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class BeginOperation {

    private final FileTransferService service;

    private final GenericDataType request;

    public BeginOperation(FileTransferService service, GenericDataType request) {
        this.service = service;
        this.request = request;
    }

    public BeginResult execute() {
        try {
            return send();
        } catch (Throwable t) {
            return operationFailed(t);
        }
    }

    private BeginResult send() throws FileTransferException {
        BeginResponse br = service.begin(request);
        return createResult(br.getTransferId());
    }

    private BeginResult createResult(String transferId) {
        if (StringUtils.isEmpty(transferId)) {
            OperationError err = new OperationError("Server returned empty transfer id");
            return new BeginResult(Type.FAIL, err);
        }
        return new BeginResult(Type.SUCCESS, transferId);
    }

    private BeginResult operationFailed(Throwable t) {
        ExceptionType type = ExceptionType.resolve(t);
        OperationError err = new OperationError("Failed to begin transfer").setCause(t).setCauseType(type);
        return new BeginResult(Type.FAIL, err);
    }
}
