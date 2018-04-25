package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.junit.Before;
import org.junit.Test;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.core.sender.items.SourceItem;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;

public class TransferTest {

    public static final String EP_ADDR = "http://localhost:7979/ws";

    public static final Path TEMP_DIR;

    static {
        String tempDir = System.getProperty("java.io.tmpdir");
        Validate.notEmpty(tempDir);
        TEMP_DIR = Paths.get(tempDir, "file-transfer-test");
    }

    @Before
    public void init() {
        try {
            if (Files.exists(TEMP_DIR)) {
                Files.walkFileTree(TEMP_DIR, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.createDirectory(TEMP_DIR);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testSingleFolder() throws InterruptedException {
        ServerConfig scfg = new ServerConfig();
        scfg.setInactiveTimeout(1000000); // TODO: 1 to debug logging
        TransferReceiverImpl receiver = publishEndpoint(TEMP_DIR, scfg);

        ClientConfig ccfg = new ClientConfig(EP_ADDR);
        ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);
        client.start();

        Collection<SourceItem> items = Collections.singletonList(new SourceDirImpl("testF1"));
        UploadRequestImpl req = new UploadRequestImpl("trans1", items); // createTransferContent(0, 1)
        Transfer transfer = client.beginUpload(req);

        sleepUntilFinished(transfer, receiver);
    }

    @Test
    public void testRandomContent() throws InterruptedException {
        ServerConfig scfg = new ServerConfig();
        scfg.setInactiveTimeout(1000000); // TODO: 1 to debug logging
        TransferReceiverImpl receiver = publishEndpoint(TEMP_DIR, scfg);

        ClientConfig ccfg = new ClientConfig(EP_ADDR);
        ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);
        client.start();

        UploadRequestImpl req = new UploadRequestImpl("trans1", (createTransferContent(3, 10)));
        Transfer transfer = client.beginUpload(req);

        sleepUntilFinished(transfer, receiver);
    }

    public static void sleepUntilFinished(Transfer transfer, TransferReceiverImpl receiver) {
        while (transfer.getStatus().getState().ordinal() < TransferState.FINISHED.ordinal() || !receiver.isTerminated()) {
            try {
                Thread.sleep(1000000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static TransferReceiverImpl publishEndpoint(Path transferId, ServerConfig cfg) {
        TransferReceiverImpl receiverImpl = new TransferReceiverImpl(transferId);
        Server receiver = FileTransfer.createServer(receiverImpl, cfg);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Endpoint.publish(EP_ADDR, receiver.getImplementor());

        receiver.start();

        return receiverImpl;
    }

    public static Collection<SourceItem> createTransferContent(int depth, int size) {
        List<SourceItem> items = new ArrayList<>(size);
        int nextDepth = depth - 1;

        for (int i = 1; i <= size; i++) {
            if (i % 2 == 0) {
                SourceDirImpl dir = new SourceDirImpl("dir-" + i);
                items.add(dir);
                if (nextDepth >= 0) {
                    Collection<SourceItem> children = createTransferContent(nextDepth, size);
                    dir.addChildren(children);
                }
            } else {
                String fileContent = "siblingId=" + i + ", siblingSize=" + size;
                long lastModified = System.currentTimeMillis();
                // TODO: what to do with invalid file name ?
                MemoryFileImpl file = MemoryFileImpl.fromStr("file-" + i + ".txt", fileContent, lastModified);
                items.add(file);
            }
        }
        return items;
    }
}
