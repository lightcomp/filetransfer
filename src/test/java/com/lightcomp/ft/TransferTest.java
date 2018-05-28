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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
import com.lightcomp.ft.client.internal.UploadTransfer;
import com.lightcomp.ft.client.operations.OperationStatus;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.client.operations.SendOperation;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.SendFrameContextImpl;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.simple.StatusStorageImpl;
import com.lightcomp.ft.xsd.v1.DirBegin;
import com.lightcomp.ft.xsd.v1.GenericDataType;
import com.lightcomp.ft.xsd.v1.XmlData;

import net.jodah.concurrentunit.Waiter;

public class TransferTest {

    private static final String SERVER_ADDR = "http://localhost:7979/";

    private static final long TEST_TIMEOUT = 5 * 60 * 1000;

    private static int testCount;

    private Path tempDir;

    private Waiter waiter;

    private Server server;

    private Client client;

    /*
     * @BeforeClass public static void beforeClass() { BasicConfigurator.configure();
     * Logger.getRootLogger().setLevel(Level.INFO); }
     */

    @Before
    public void before() throws IOException {
        tempDir = Files.createTempDirectory("file-transfer-tests");
        waiter = new Waiter();
    }

    @After
    public void after() throws IOException {
        if (client != null) {
            client.stop();
            client = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
        if (tempDir != null) {
            PathUtils.deleteWithChildren(tempDir);
            tempDir = null;
        }
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
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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
        client = FileTransfer.createClient(ccfg);

        client.start();

        List<SourceItem> items = Collections.singletonList(new MemoryDir("test"));
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FINISHED);

        Transfer transfer = client.upload(request);

        waiter.await(TEST_TIMEOUT, 2);

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
        Assert.assertTrue(sts.getTransferedSeqNum() == 1);
    }

    @Test
    public void testInactiveTransfer() throws TimeoutException {
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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
        client = FileTransfer.createClient(ccfg);

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

        waiter.await(TEST_TIMEOUT, 2);
    }

    @Test
    public void testMaxFrameSizeUpload() throws TimeoutException {
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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
        ccfg.setMaxFrameSize(70); // 70B
        client = FileTransfer.createClient(ccfg);

        client.start();

        MemoryDir dir = new MemoryDir("test");
        dir.addChild(new MemoryFile("1.txt", new byte[0], 0)); // empty
        dir.addChild(new MemoryFile("2.txt", new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45 }, 0)); // 5 bytes
        dir.addChild(new GeneratedFile("3.txt", 10, 0)); // 10B which overlaps first frame by 5 bytes
        List<SourceItem> items = Collections.singletonList(dir);
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FINISHED);

        client.upload(request);

        waiter.await(TEST_TIMEOUT, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 15);
        Assert.assertTrue(sts.getTransferedSeqNum() == 3);
    }

    @Test
    public void testMaxFrameBlocksUpload() throws TimeoutException {
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandlerImpl createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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
        client = FileTransfer.createClient(ccfg);

        client.start();

        List<SourceItem> items = Arrays.asList(new MemoryDir("1"), new MemoryDir("2"), new MemoryDir("3"));
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), items, waiter, TransferState.FINISHED);

        client.upload(request);

        waiter.await(TEST_TIMEOUT, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 0);
        Assert.assertTrue(sts.getTransferedSeqNum() == 2);
    }

    @Test
    public void testInvalidChecksum() throws TimeoutException {
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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
        client = FileTransfer.createClient(ccfg);

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

        waiter.await(TEST_TIMEOUT, 2);
    }

    @Test
    public void testMixedContentUpload() throws TimeoutException {
        int blockMax = 5;

        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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
        client = FileTransfer.createClient(ccfg);

        client.start();

        Pair<Collection<SourceItem>, Integer> pair = createMixedContent(3, blockMax);
        UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), pair.getLeft(), waiter,
                TransferState.FINISHED);

        client.upload(request);

        waiter.await(TEST_TIMEOUT, 2);

        server.stop();

        // test storage status after server stopped
        int blockCount = pair.getRight() / blockMax * 2;
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getTransferedSeqNum() == blockCount);
        Assert.assertTrue(sts.getTransferedSize() == 0);
    }

    @Test
    public void testInvalidFrameUpload() throws TimeoutException {
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
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

        client = new ClientImpl(ccfg) {
            @Override
            public Transfer upload(UploadRequest request) {
                AbstractTransfer transfer = new UploadTransfer(request, config, service) {
                    @Override
                    protected boolean transferFrames() {
                        SendFrameContextImpl frameCtx = new SendFrameContextImpl(1, config);
                        DirBegin db = new DirBeginBlockImpl();
                        db.setN("test");
                        frameCtx.addBlock(db);
                        frameCtx.addBlock(db); // unclosed child directory -> failure
                        frameCtx.addBlock(new DirEndBlockImpl());
                        frameCtx.setLast(true);
                        SendOperation so = new SendOperation(this, service, frameCtx);
                        OperationStatus sos = so.execute();
                        if (sos.getType() != Type.SUCCESS) {
                            transferFailed(sos);
                            return false;
                        }
                        return true;
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

        waiter.await(TEST_TIMEOUT, 2);
    }

    @Test
    public void testUploadRequestResponse() throws TimeoutException, ParserConfigurationException {
        UploadTransferHandler ur = new UploadTransferHandler(tempDir) {
            @Override
            protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
                return new UploadHandlerImpl(transferId, request, request.getId(), uploadDir, server, waiter,
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
        client = FileTransfer.createClient(ccfg);

        client.start();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        Element el = doc.createElement("el");

        GenericDataType req = new GenericDataType();
        req.setId("id");
        req.setBinData(new byte[] { 3 });
        XmlData xmlData = new XmlData();
        xmlData.getAnies().add(el);
        req.setXmlData(xmlData);

        UploadRequestImpl request = new UploadRequestImpl(req, Collections.emptyList(), waiter,
                TransferState.FINISHED) {
            @Override
            public void onTransferSuccess(GenericDataType response) {
                waiter.assertTrue(response.getId().equals("id"));
                waiter.assertTrue(response.getBinData()[0] == 3);
                waiter.assertTrue(response.getXmlData().getAnies().get(0).getTagName().equals("el"));
                super.onTransferSuccess(response);
            }
        };

        client.upload(request);

        waiter.await(TEST_TIMEOUT, 2);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(ur.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getResponse().getId().equals("id"));
        Assert.assertTrue(sts.getResponse().getBinData()[0] == 3);
        Assert.assertTrue(sts.getResponse().getXmlData().getAnies().get(0).getTagName().equals("el"));
    }

    @Test
    public void testFolderDownload() throws TimeoutException, IOException {
        List<SourceItem> items = Collections.singletonList(new MemoryDir("test"));

        DwnldTransferHandler dth = new DwnldTransferHandler() {
            @Override
            protected DownloadHandler createDownload(String transferId, GenericDataType request) {
                return new DwnldHandlerImpl(transferId, null, request.getId(), items, server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(dth, ss);
        scfg.setInactiveTimeout(60);

        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        // ccfg.setSoapLogging(true);
        client = FileTransfer.createClient(ccfg);

        client.start();

        DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FINISHED);

        Transfer transfer = client.download(request);

        waiter.await(TEST_TIMEOUT, 2);

        TransferStatus cts = transfer.getStatus();
        Assert.assertTrue(cts.getState() == TransferState.FINISHED);
        Assert.assertTrue(cts.getTransferedSize() == 0);

        // store will have status after server stop
        Assert.assertTrue(ss.getTransferStatus(dth.getLastTransferId()) == null);

        server.stop();

        // test storage status after server stopped
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(dth.getLastTransferId());
        Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
        Assert.assertTrue(sts.getTransferedSize() == 0);
        Assert.assertTrue(sts.getTransferedSeqNum() == 1);
    }

    @Test
    public void testMixedContentDownload() throws TimeoutException {
        int blockMax = 5;
        Pair<Collection<SourceItem>, Integer> pair = createMixedContent(3, blockMax);

        DwnldTransferHandler dth = new DwnldTransferHandler() {
            @Override
            protected DownloadHandler createDownload(String transferId, GenericDataType request) {
                return new DwnldHandlerImpl(transferId, null, request.getId(), pair.getLeft(), server, waiter,
                        com.lightcomp.ft.server.TransferState.FINISHING);
            }
        };
        StatusStorageImpl ss = new StatusStorageImpl();
        ServerConfig scfg = new ServerConfig(dth, ss);
        scfg.setInactiveTimeout(60);
        scfg.setMaxFrameBlocks(blockMax);
        
        String addr = publishEndpoint(scfg);

        ClientConfig ccfg = new ClientConfig(addr);
        ccfg.setRecoveryDelay(2);
        // ccfg.setSoapLogging(true);
        client = FileTransfer.createClient(ccfg);

        client.start();

        DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FINISHED);

        client.download(request);

        waiter.await(TEST_TIMEOUT, 2);

        server.stop();

        // test storage status after server stopped
        int blockCount = pair.getRight() / blockMax * 2;
        com.lightcomp.ft.server.TransferStatus sts = ss.getTransferStatus(dth.getLastTransferId());
        Assert.assertTrue(sts.getTransferedSeqNum() == blockCount);
        Assert.assertTrue(sts.getTransferedSize() == 0);
    }

    public static GenericDataType createReqData(String id) {
        GenericDataType gd = new GenericDataType();
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
