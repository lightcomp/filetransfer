package com.lightcomp.ft;

import com.lightcomp.ft.receiver.BeginTransferListener;
import com.lightcomp.ft.receiver.ReceiverService;
import com.lightcomp.ft.receiver.impl.ReceiverServiceImpl;
import com.lightcomp.ft.sender.SenderConfig;
import com.lightcomp.ft.sender.SenderService;
import com.lightcomp.ft.sender.impl.SenderServiceImpl;

public class FileTransfer {

    public static SenderService createSenderService(SenderConfig senderConfig) {
        SenderServiceImpl impl = new SenderServiceImpl(senderConfig);
        return impl;
    }

    public static ReceiverService createReceiverService(BeginTransferListener beginTransferListener) {
        ReceiverServiceImpl impl = new ReceiverServiceImpl(beginTransferListener);
        return impl;
    }
}
