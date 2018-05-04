package com.lightcomp.ft;

import java.io.IOException;
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
import java.util.concurrent.TimeoutException;

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;

import net.jodah.concurrentunit.Waiter;

public class TransferTest {

    public static Path tempDir;

    public Server publishEndpoint(String address, ServerConfig cfg) {
        Server server = FileTransfer.createServer(cfg);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Endpoint.publish(address, server.getImplementor());

        server.start();

        return server;
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        String temp = System.getProperty("java.io.tmpdir");
        Path tempPath = Paths.get(temp);
        tempDir = Files.createTempDirectory(tempPath, "file-transfer-tests");
    }

    @AfterClass
    public static void afterClass() throws IOException {
        if (tempDir != null) {
            Files.delete(tempDir);
            tempDir = null;
        }
    }

    @After
    public void after() throws IOException {
        Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!tempDir.equals(dir)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void testSingleFolder() throws TimeoutException {
        Waiter waiter = new Waiter();

        SimpleUploadReceiver ur = new SimpleUploadReceiver(tempDir, waiter);
        SimpleStatusStorage ss = new SimpleStatusStorage();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        Server server = publishEndpoint("http://localhost:7979/1", scfg);
        ur.setServer(server);

        ClientConfig ccfg = new ClientConfig("http://localhost:7979/1");
        ccfg.setRecoveryDelay(2);
        ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        Collection<SourceItem> items = Collections.singletonList(new SimpleDir("test"));
        UploadRequestImpl request = new UploadRequestImpl("req", items, waiter);

        Transfer transfer = client.upload(request);

        waiter.await(10 * 1000, 2);

        TransferStatus cts = transfer.getStatus();
        Assert.assertTrue(cts.getState() == TransferState.FINISHED);
        Assert.assertTrue(cts.getTransferedSize() == 0);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 0);
        Assert.assertTrue(sts.getLastFrameSeqNum() == 1);
    }

    @Test(expected = AssertionError.class)
    public void testInactiveTransfer() throws TimeoutException {
        Waiter waiter = new Waiter();

        SimpleUploadReceiver ur = new SimpleUploadReceiver(tempDir, waiter);
        SimpleStatusStorage ss = new SimpleStatusStorage();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(1); // short enough to get terminated

        Server server = publishEndpoint("http://localhost:7979/2", scfg);
        ur.setServer(server);

        ClientConfig ccfg = new ClientConfig("http://localhost:7979/2");
        ccfg.setRecoveryDelay(5);
        ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        Collection<SourceItem> items = Collections.singletonList(new SimpleDir("test"));
        UploadRequestImpl request = new UploadRequestImpl("req", items, waiter) {
            @Override
            public void onTransferProgress(TransferStatus status) {
                super.onTransferProgress(status);
                try {
                    Thread.sleep(1000 * 2); // delay long enough to cause inactive failure
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        client.upload(request);

        waiter.await(10 * 1000, 2);
    }

    @Test
    public void testDifferentFileSize() throws TimeoutException {
        Waiter waiter = new Waiter();

        SimpleUploadReceiver ur = new SimpleUploadReceiver(tempDir, waiter);
        SimpleStatusStorage ss = new SimpleStatusStorage();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        Server server = publishEndpoint("http://localhost:7979/3", scfg);
        ur.setServer(server);

        ClientConfig ccfg = new ClientConfig("http://localhost:7979/3");
        ccfg.setRecoveryDelay(2);
        ccfg.setSoapLogging(true);
        ccfg.setMaxFrameSize(100 * 1024); // 100kB
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        SimpleDir dir = new SimpleDir("test");
        dir.addChild(new InMemoryFile("1.txt", new byte[0], 0)); // empty
        dir.addChild(new InMemoryFile("2.txt", new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45 }, 0)); // 5 bytes
        dir.addChild(new GeneratedFile("3.txt", 100 * 1024, 0)); // 100kB which overlaps first frame by 5 bytes
        UploadRequestImpl request = new UploadRequestImpl("req", Collections.singletonList(dir), waiter);

        client.upload(request);

        waiter.await(10 * 1000, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 100 * 1024 + 5);
        Assert.assertTrue(sts.getLastFrameSeqNum() == 2);
    }

    // TODO: rework to stream and use as test
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
                String content = "ABCDEFGHCHIJKLMNOPQRSTUVWXYZ !@#$%^&*()_+{}:\"|<>?-=[];',./\\ 1234567890 ěščřžýáíéť";
                long lastModified = System.currentTimeMillis();
                InMemoryFile file = InMemoryFile.fromString(i + ".txt", lastModified, content);
                items.add(file);
            }
        }
        return items;
    }
}
