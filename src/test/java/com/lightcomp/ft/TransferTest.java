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
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.junit.Before;
import org.junit.Test;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.core.SourceItem;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.Server;

public class TransferTest {

    public static final String EP_ADDR = "http://localhost:7979/ws";

    public static final Path TEMP_DIR;

    public static final ChecksumType CHST = ChecksumType.SHA_512;

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
    public void test() throws InterruptedException {
        ServerConfig rcfg = new ServerConfig();
        rcfg.setInactiveTimeout(10); // TODO: 1 to debug logging
        publishEndpoint(TEMP_DIR, rcfg);

        ClientConfig scfg = new ClientConfig(EP_ADDR);
        scfg.setSoapLogging(true);
        Client sender = FileTransfer.createSenderService(scfg);
        sender.start();

        TransferRequestImpl req = new TransferRequestImpl("trans1", CHST, createTransferContent(3, 30));
        Transfer transfer = sender.beginTransfer(req);

        sleepUntilFinished(transfer);
    }

    public static void sleepUntilFinished(Transfer transfer) {
        while (transfer.getStatus().getState().ordinal() < TransferState.COMMITTED.ordinal()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void publishEndpoint(Path transferId, ServerConfig cfg) {
        BeginTransferListenerImpl listenerImpl = new BeginTransferListenerImpl(transferId);
        Server receiver = FileTransfer.createReceiverService(listenerImpl, cfg);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        Endpoint.publish(EP_ADDR, receiver.getImplementor());

        receiver.start();
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
