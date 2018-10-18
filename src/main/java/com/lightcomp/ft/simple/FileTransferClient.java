package com.lightcomp.ft.simple;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.server.TransferDataHandler.Mode;
import com.lightcomp.ft.xsd.v1.GenericDataType;

/**
 * input parameters:<br>
 * 1) server URL<br>
 * 2) request type - UPLOAD/DOWNLOAD<br>
 * 3) requestId - server upload or download directory<br>
 * 4) client directory
 * 5) transfer type - optional, e.g. SIP_UPLOAD
 */
public class FileTransferClient {

    private static final Logger logger = LoggerFactory.getLogger(FileTransferClient.class);

    public static void main(String[] args) {
        /*  
         * BasicConfigurator.configure();
         * Logger.getRootLogger().setLevel(Level.INFO);
         */
        logger.debug("Starting FileTransferClient, params: {} {} {} {}", args[0], args[1], args[2], args[3]);

        ClientConfig cfg = new ClientConfig(args[0]);
        cfg.setRecoveryDelay(5);

        Client client = FileTransfer.createClient(cfg);
        client.start();

        Mode mode = Mode.valueOf(args[1]);        
        Path clientDir = Paths.get(args[3]);
        String transferType;
        if(args.length>4) {
        	transferType = args[4];
        } else {
        	transferType = args[1];
        }
        AbstractRequest request = startTransfer(client, mode, clientDir, args[2], transferType);

        while (!request.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        client.stop();
        System.exit(0);
    }

    private static AbstractRequest startTransfer(Client client, Mode mode, Path clientDir, String serverDir, String transferType) {
        GenericDataType data = new GenericDataType();
        data.setType(transferType);
        data.setId(serverDir);

        if (mode == Mode.UPLOAD) {
            UploadRequestImpl ur = new UploadRequestImpl(clientDir, data);
            client.upload(ur);
            return ur;
        }
        DwnldRequestImpl dr = new DwnldRequestImpl(clientDir, data);
        client.download(dr);
        return dr;
    }
}
