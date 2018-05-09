package com.lightcomp.ft.simple;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.lightcomp.ft.FileTransfer;
import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;

public class FileTransferClient {

    public static void main(String[] args) {

        ClientConfig cfg = new ClientConfig(args[0]);
        cfg.setRecoveryDelay(5);

        Client client = FileTransfer.createClient(cfg);
        client.start();

        Path dataDir = Paths.get(args[1]);
        UploadRequestImpl upload = new UploadRequestImpl(dataDir, null);
        client.upload(upload);

        try {
            Thread.sleep(1000 * 60 * 10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
