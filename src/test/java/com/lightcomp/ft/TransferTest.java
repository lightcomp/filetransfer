package com.lightcomp.ft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Endpoint;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.internal.AbstractTransfer;
import com.lightcomp.ft.client.internal.ClientImpl;
import com.lightcomp.ft.client.internal.UploadFrameContext;
import com.lightcomp.ft.client.internal.UploadTransfer;
import com.lightcomp.ft.client.operations.SendOperation;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.simple.StatusStorageImpl;
import com.lightcomp.ft.xsd.v1.DirBegin;
import com.lightcomp.ft.xsd.v1.GenericData;

import net.jodah.concurrentunit.Waiter;

public class TransferTest {

    private static final String SERVER_ADDR = "http://localhost:7979/";

    private static int testCount;

    private Path tempDir;

    private Server server;

    private Waiter waiter;

    @BeforeClass
    public static void beforeClass() {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void before() throws IOException {
        tempDir = Files.createTempDirectory("file-transfer-tests");
        waiter = new Waiter();
    }

    @After
    public void after() throws IOException {
        if (server != null) {
            server.stop();
        }
        if (tempDir == null) {
            return;
        }
        PathUtils.deleteWithChildren(tempDir);
    }

    public String publishEndpoint(ServerConfig cfg) {
        server = FileTransfer.createServer(cfg);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);

        testCount++;
        String address = SERVER_ADDR + testCount;
        Endpoint.publish(address, server.getImplementor());

        server.start();

        return address;
    }

    @Test
    public void testFolderUpload() throws TimeoutException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        // ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        List<SourceItem> items = Collections.singletonList(new MemoryDir("test"));
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FINISHED);

        Transfer transfer = client.upload(request);

        waiter.await(10 * 1000, 2);

        TransferStatus cts = transfer.getStatus();
        Assert.assertTrue(cts.getState() == TransferState.FINISHED);
        Assert.assertTrue(cts.getTransferedSize() == 0);

        // store will have status after server stop
        Assert.assertTrue(ss.getTransferStatus(ur.getLastTransferId()) == null);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 0);
        Assert.assertTrue(sts.getLastFrameSeqNum() == 1);
    }

    @Test
    public void testInactiveTransfer() throws TimeoutException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FAILED);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(2); // short enough to get terminated

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(5);
        // ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        List<SourceItem> items = Collections.singletonList(new MemoryDir("test"));
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FAILED) {
            @Override
            public void onTransferProgress(TransferStatus status) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                super.onTransferProgress(status);
            }
        };

        client.upload(request);

        waiter.await(10 * 1000, 2);
    }

    @Test
    public void testMaxFrameSizeUpload() throws TimeoutException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        // ccfg.setSoapLogging(true);
        ccfg.setMaxFrameSize(100 * 1024); // 100kB
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        MemoryDir dir = new MemoryDir("test");
        dir.addChild(new MemoryFile("1.txt", new byte[0], 0)); // empty
        dir.addChild(new MemoryFile("2.txt", new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45 }, 0)); // 5 bytes
        dir.addChild(new GeneratedFile("3.txt", 100 * 1024, 0)); // 100kB which overlaps first frame by 5 bytes
        List<SourceItem> items = Collections.singletonList(dir);
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FINISHED);

        client.upload(request);

        waiter.await(10 * 1000, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 100 * 1024 + 5);
        Assert.assertTrue(sts.getLastFrameSeqNum() == 2);
    }

    @Test
    public void testMaxFrameBlocksUpload() throws TimeoutException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        ccfg.setMaxFrameBlocks(5); // 3 directories = 6 blocks, must be 2 frames
        // ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        List<SourceItem> items = Arrays.asList(new MemoryDir("1"), new MemoryDir("2"), new MemoryDir("3"));
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FINISHED);

        client.upload(request);

        waiter.await(10 * 1000, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 0);
        Assert.assertTrue(sts.getLastFrameSeqNum() == 2);
    }

    @Test
    public void testInvalidChecksum() throws TimeoutException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FAILED);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        ccfg.setMaxFrameBlocks(5); // 3 directories = 6 blocks, must be 2 frames
        // ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        MemoryFile file = new MemoryFile("1.txt", new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45 }, 0); // ABCDE
        // valid checksum
        byte[] chksm = DatatypeConverter.parseHexBinary(
                "9989A8FCBC29044B5883A0A36C146FE7415B1439E995B4D806EA0AF7DA9CA4390EB92A604B3ECFA3D75F9911C768FBE2AECC59EFF1E48DCAECA1957BDDE01DFB");
        // invalid checksum
        chksm[0] = 0x00;
        file.setChecksum(chksm);
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), Collections.singletonList(file), waiter,
                TransferState.FAILED);

        client.upload(request);

        waiter.await(10 * 1000, 2);
    }

    @Test
    public void testMixedContentUpload() throws TimeoutException {
        int blockMax = 5;

        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        ccfg.setMaxFrameBlocks(blockMax);
        // ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        Pair<Collection<SourceItem>, Integer> pair = createMixedContent(3, blockMax);
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), pair.getLeft(), waiter, TransferState.FINISHED);

        client.upload(request);

        waiter.await(10 * 1000, 2);

        server.stop();

        // test storage status after server stopped
        int blockCount = pair.getRight() / blockMax * 2;
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getLastFrameSeqNum() == blockCount);
        Assert.assertTrue(sts.getTransferedSize() == 0);
    }

    @Test
    public void testInvalidFrameUpload() throws TimeoutException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, null, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FAILED);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        // ccfg.setSoapLogging(true);

        Client client = new ClientImpl(ccfg) {
            @Override
            public Transfer upload(UploadRequest request) {
                AbstractTransfer transfer = new UploadTransfer(request, config, service) {
                    @Override
                    protected boolean transferFrames() {
                        UploadFrameContext frameCtx = new UploadFrameContext(1, config);
                        DirBegin db = new DirBeginBlockImpl();
                        db.setN("test");
                        frameCtx.addBlock(db);
                        frameCtx.addBlock(db); // unclosed child directory -> failure
                        frameCtx.addBlock(new DirEndBlockImpl());
                        frameCtx.setLast(true);
                        SendOperation op = new SendOperation(this, this, frameCtx);
                        return op.execute(service);
                    }
                };
                transferExecutor.addTask(transfer);
                return transfer;
            }
        };

        client.start();

        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), Collections.emptyList(), waiter,
                TransferState.FAILED);

        client.upload(request);

        waiter.await(10 * 1000, 2);
    }

    @Test
    public void testUploadRequestResponse() throws TimeoutException, ParserConfigurationException {
        UploadReceiver ur = new UploadReceiver(tempDir) {
            @Override
            protected UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request) {
                return new UploadAcceptorImpl(transferId, request, request.getId(), uploadDir, server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(ur, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        // ccfg.setSoapLogging(true);
        Client client = FileTransfer.createClient(ccfg);

        client.start();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        Element el = doc.createElement("el");

        GenericData req = new GenericData();
        req.setId("id");
        req.setBinData(new byte[] { 3 });
        req.setAny(el);

        UploadRequestImpl request = new UploadRequestImpl(req, Collections.emptyList(), waiter, TransferState.FINISHED) {
            @Override
            public void onTransferSuccess(GenericData response) {
                waiter.assertTrue(response.getId().equals("id"));
                waiter.assertTrue(response.getBinData()[0] == 3);
                waiter.assertTrue(response.getAny().getTagName().equals("el"));
                super.onTransferSuccess(response);
            }
        };

        client.upload(request);

        waiter.await(10 * 1000, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getResponse().getId().equals("id"));
        Assert.assertTrue(sts.getResponse().getBinData()[0] == 3);
        Assert.assertTrue(sts.getResponse().getAny().getTagName().equals("el"));
    }

    public static GenericData createReqData(String id) {
        GenericData gd = new GenericData();
        gd.setId(id);
        return gd;
    }

    public static Pair<Collection<SourceItem>, Integer> createMixedContent(int depth, int size) {
        List<SourceItem> items = new ArrayList<>(size);
        int count = size;
        for (int i = 1; i <= size; i++) {
            String cnt = Integer.toString(i);
            if (i % 2 == 0) {
                MemoryDir dir = new MemoryDir(cnt);
                items.add(dir);
                if (depth > 0) {
                    Pair<Collection<SourceItem>, Integer> pair = createMixedContent(depth - 1, size);
                    dir.addChildren(pair.getLeft());
                    count += pair.getRight();
                }
            } else {
                MemoryFile file = new MemoryFile(cnt, new byte[0], 0);
                items.add(file);
            }
        }
        return Pair.of(items, count);
    }
}
