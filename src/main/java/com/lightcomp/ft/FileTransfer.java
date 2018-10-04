package com.lightcomp.ft;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.internal.ClientImpl;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.internal.ServerImpl;

/**
 * Main class for file transfer.
 */
public class FileTransfer {

    /**
     * Creates client with given configuration.
     */
    public static Client createClient(ClientConfig config) {
        Client client = new ClientImpl(config);
        return client;
    }

    /**
     * Creates server with given configuration. Multiple server instances can be
     * running simultaneously.
     */
    public static Server createServer(ServerConfig config) {
        ServerImpl server = new ServerImpl(config);
        return server;
    }
}
