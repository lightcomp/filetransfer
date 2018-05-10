package com.lightcomp.ft.simple;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;

public class FileTransferServer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FileTransferServer.class);

    public static void main(String[] args) {
    	
    	LOGGER.debug("Starting FileTransferServer {} {}", args[0], args[1]);

        Path transferDir = Paths.get(args[1]);
        SimpleReceiver receiver = new SimpleReceiver(transferDir);
        SimpleStatusStorage statusStorage = new SimpleStatusStorage();
        ServerConfig cfg = new ServerConfig(receiver, statusStorage);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Server server = FileTransfer.createServer(cfg);
        Endpoint.publish(args[0], server.getImplementor());
        server.start();

        try {
            Thread.sleep(1000 * 60 * 10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
