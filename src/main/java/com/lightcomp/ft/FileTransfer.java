package com.lightcomp.ft;

import com.lightcomp.ft.receiver.BeginTransferListener;
import com.lightcomp.ft.receiver.ReceiverConfig;
import com.lightcomp.ft.receiver.ReceiverService;
import com.lightcomp.ft.receiver.impl.ReceiverServiceImpl;
import com.lightcomp.ft.sender.SenderConfig;
import com.lightcomp.ft.sender.SenderService;
import com.lightcomp.ft.sender.impl.SenderServiceImpl;

public class FileTransfer {

    public static SenderService createSenderService(SenderConfig config) {
        SenderServiceImpl impl = new SenderServiceImpl(config);
        return impl;
    }

    public static ReceiverService createReceiverService(BeginTransferListener beginTransferListener, ReceiverConfig config) {
        ReceiverServiceImpl impl = new ReceiverServiceImpl(beginTransferListener, config);
        return impl;
    }
}
