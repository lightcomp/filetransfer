package com.lightcomp.ft;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.internal.ClientImpl;
import com.lightcomp.ft.server.BeginTransferListener;
import com.lightcomp.ft.server.ReceiverConfig;
import com.lightcomp.ft.server.ReceiverService;
import com.lightcomp.ft.server.impl.ReceiverServiceImpl;

public class FileTransfer {

    public static Client createClient(ClientConfig config) {
        Client client = new ClientImpl(config);
        return client;
    }

    public static ReceiverService createReceiverService(BeginTransferListener beginTransferListener, ReceiverConfig config) {
        ReceiverServiceImpl impl = new ReceiverServiceImpl(beginTransferListener, config);
        return impl;
    }
}
