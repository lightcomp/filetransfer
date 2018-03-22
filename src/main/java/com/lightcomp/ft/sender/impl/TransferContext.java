package com.lightcomp.ft.sender.impl;

import java.util.Collection;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.sender.SenderConfig;
import com.lightcomp.ft.sender.SourceItem;

import cxf.FileTransferService;

public interface TransferContext extends TransferInfo {

    ChecksumType getChecksumType();

    Collection<SourceItem> getSourceItems();

    SenderConfig getSenderConfig();

    FileTransferService getService();

    void sleep(long ms, boolean ignoreCancel);

    void setTransferId(String transferId);

    void onFilePrepared(long size);

    void onDataSent(long size);

    void onTransferRecovery();
}
