package com.lightcomp.ft.simple;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.EndpointImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;

/**
 * input params:<br>
 * 1) server URL<br>
 * 2) work directory<br>
 * <br>
 * request.type: UPLOAD<br>
 * - request.id used as upload directory (fails if already exists)<br>
 * request.type: DOWNLOAD<br>
 * - request.id used as source directory (fails if does not exist)<br>
 */
public class FileTransferServer {

    private static final Logger logger = LoggerFactory.getLogger(FileTransferServer.class);

    public static void main(String[] args) throws IOException {
        /*  
         * BasicConfigurator.configure();
         * Logger.getRootLogger().setLevel(Level.INFO);
         */
        logger.debug("Starting FileTransferServer, params: {} {}", args[0], args[1]);

        Path workDir = Paths.get(args[1]);
        TransferHandlerImpl handler = new TransferHandlerImpl(workDir);
        StatusStorageImpl statusStorage = new StatusStorageImpl();
        ServerConfig cfg = new ServerConfig(handler, statusStorage);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Server server = FileTransfer.createServer(cfg);
        server.start();

        EndpointImpl ep = server.getEndpointFactory().createCxfEndpoint();
        ep.setBus(BusFactory.getThreadDefaultBus());
        ep.setAddress(args[0]);
        ep.publish();
        
        System.out.println("Press any key to exit ...");
        try {
            System.in.read();
        } finally {
            server.stop();
            ep.stop();
            System.exit(0);
        }
    }
}
