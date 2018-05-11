package com.lightcomp.ft.simple;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;

public class FileTransferServer {

    private static final Logger logger = LoggerFactory.getLogger(FileTransferServer.class);

    public static void main(String[] args) throws IOException {
        BasicConfigurator.configure();
        logger.debug("Starting FileTransferServer {} {}", args[0], args[1]);

        Path transferDir = Paths.get(args[1]);
        ReceiverImpl receiver = new ReceiverImpl(transferDir);
        StatusStorageImpl statusStorage = new StatusStorageImpl();
        ServerConfig cfg = new ServerConfig(receiver, statusStorage);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Server server = FileTransfer.createServer(cfg);
        server.start();
        
        Endpoint endpoint = Endpoint.publish(args[0], server.getImplementor());
        
        System.out.println("Press any key to exit ...");
        try {
            System.in.read();
        } finally {
            server.stop();
            endpoint.stop();
            System.exit(0);
        }
    }
}
