package com.lightcomp.ft.simple;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.EndpointImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferDataHandler.Mode;

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

        Set<String> uploadModes = new HashSet<>();
        if (args.length>2) {
        	String [] splitted = args[2].split(",");
        	uploadModes.addAll(Arrays.asList(splitted));
        } else {
        	uploadModes.add(Mode.UPLOAD.name());
        }
        
	Set<String> downloadModes = new HashSet<>();
        if (args.length>3) {
        	String [] splitted = args[3].split(",");
        	downloadModes.addAll(Arrays.asList(splitted));
        } else {
        	downloadModes.add(Mode.DOWNLOAD.name());
        }
        
        
        
        Path workDir = Paths.get(args[1]);
        TransferHandlerImpl handler = new TransferHandlerImpl(workDir,downloadModes,uploadModes);
        StatusStorageImpl statusStorage = new StatusStorageImpl();
        ServerConfig cfg = new ServerConfig(handler, statusStorage);

        Server server = FileTransfer.createServer(cfg);
        server.start();

        EndpointImpl ep = server.getEndpointFactory().createCxf(BusFactory.getThreadDefaultBus());
        ep.publish(args[0]);
        
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
