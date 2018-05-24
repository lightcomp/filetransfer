package com.lightcomp.ft.simple;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;

/**
 * input parameters:<br>
 * 1) server URL<br>
 * 2) mode - UPLOAD/DOWNLOAD<br>
 * 3) requestId - server upload or download directory<br>
 * 4) client directory
 */
public class FileTransferClient {

    private static final Logger logger = LoggerFactory.getLogger(FileTransferClient.class);

    public static void main(String[] args) {
        // BasicConfigurator.configure();
        logger.debug("Starting client");

        ClientConfig cfg = new ClientConfig(args[0]);
        cfg.setRecoveryDelay(5);

        Client client = FileTransfer.createClient(cfg);
        client.start();

        Path dataDir = Paths.get(args[1]);
        UploadRequestImpl upload = new UploadRequestImpl(dataDir, null);
        client.upload(upload);

        // wait for transfer finish
        client.stop();
    }
}
