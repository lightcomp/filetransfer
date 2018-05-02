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
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferStatusStorage;

public class TransferTest {

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
        scfg.setInactiveTimeout(5);
        SimpleUploadReceiver receiver = publishEndpoint("http://localhost:7979/1", scfg);

        ClientConfig ccfg = new ClientConfig("http://localhost:7979/1");
        ccfg.setRecoveryDelay(5);
        ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);
        client.start();

        Collection<SourceItem> items = Collections.singletonList(new SimpleDir("testF1"));
        UploadRequestImpl req = new UploadRequestImpl("trans1", items);
        client.upload(req);

        waitUntilFinished(req, receiver);
    }

    @Test
    public void testRandomContent() throws InterruptedException {
        ServerConfig scfg = new ServerConfig();
        scfg.setInactiveTimeout(5);
        SimpleUploadReceiver receiver = publishEndpoint("http://localhost:7979/2", scfg);

        ClientConfig ccfg = new ClientConfig("http://localhost:7979/2");
        ccfg.setRecoveryDelay(5);
        ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);
        client.start();

        UploadRequestImpl req = new UploadRequestImpl("trans1", createTransferContent(4, 10));
        client.upload(req);

        waitUntilFinished(req, receiver);
    }

    public synchronized void waitUntilFinished(UploadRequestImpl request, SimpleUploadReceiver receiver) {
        while (!request.isFinished() || !receiver.isTerminated()) {
            try {
                wait(100L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public SimpleUploadReceiver publishEndpoint(String address, ServerConfig cfg) {
        SimpleUploadReceiver receiverImpl = new SimpleUploadReceiver(TEMP_DIR);
        TransferStatusStorage tss = new TransferStatusStorageImpl();
        Server receiver = FileTransfer.createServer(receiverImpl, cfg, tss);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Endpoint.publish(address, receiver.getImplementor());

        receiver.start();

        return receiverImpl;
    }

    public static Collection<SourceItem> createTransferContent(int depth, int size) {
        List<SourceItem> items = new ArrayList<>(size);
        int nextDepth = depth - 1;

        for (int i = 1; i <= size; i++) {
            if (i % 2 == 0) {
                SimpleDir dir = new SimpleDir(Integer.toString(i));
                items.add(dir);
                if (nextDepth >= 0) {
                    Collection<SourceItem> children = createTransferContent(nextDepth, size);
                    dir.addChildren(children);
                }
            } else {
                String content = "ABCDEFGHCHIJKLMNOPQRSTUVWXYZ !@#$%^&*()_+{}:\"|<>?-=[];',./\\ 1234567890";
                long lastModified = System.currentTimeMillis();
                InMemoryFile file = InMemoryFile.fromString(i + ".txt", lastModified, content);
                items.add(file);
            }
        }
        return items;
    }
}
