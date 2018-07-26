package com.lightcomp.ft;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.internal.ClientImpl;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.internal.ServerImpl;

public class FileTransfer {

	public static Client createClient(ClientConfig config) {
		Client client = new ClientImpl(config);
		return client;
	}

	public static Server createServer(ServerConfig config) {
		ServerImpl server = new ServerImpl(config);
		return server;
	}
}
