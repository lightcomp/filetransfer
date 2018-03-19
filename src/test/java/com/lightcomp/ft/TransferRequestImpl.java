package com.lightcomp.ft;

import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.sender.SourceItem;
import com.lightcomp.ft.sender.TransferRequest;
import com.lightcomp.ft.sender.TransferStatus;

public class TransferRequestImpl implements TransferRequest {

    private static final Logger logger = LoggerFactory.getLogger(TransferRequestImpl.class);
    
    @Override
    public String getRequestId() {
        return "TestRequestId";
    }

    @Override
    public ChecksumType getChecksumType() {
        return ChecksumType.SHA_384;
    }

    @Override
    public Collection<SourceItem> getItems() {
        return Arrays.asList(new SourceDirImpl()); //, new SourceFileImpl());
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTransferSuccess() {
        logger.error("Transfer success");
    }

    @Override
    public void onTransferAborted() {
        logger.error("Transfer canceled");
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        logger.error("Transfer failed");
    }
}
