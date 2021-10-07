package com.lightcomp.ft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.EndpointImpl;
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
import com.lightcomp.ft.client.internal.ClientImpl;
import com.lightcomp.ft.client.internal.UploadTransfer;
import com.lightcomp.ft.client.internal.operations.OperationResult;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.client.internal.operations.SendOperation;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.blocks.DirBeginBlockImpl;
import com.lightcomp.ft.core.blocks.DirEndBlockImpl;
import com.lightcomp.ft.core.send.SendFrameContextImpl;
import com.lightcomp.ft.core.send.items.BaseDir;
import com.lightcomp.ft.core.send.items.ListReader;
import com.lightcomp.ft.core.send.items.MemoryFile;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.core.send.items.SourceItemReader;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.simple.StatusStorageImpl;
import com.lightcomp.ft.xsd.v1.DirBegin;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;
import com.lightcomp.ft.xsd.v1.ReceiveRequest;
import com.lightcomp.ft.xsd.v1.SendRequest;
import com.lightcomp.ft.xsd.v1.XmlData;

import net.jodah.concurrentunit.Waiter;

public class TransferTest {

	private static final SourceItemReader TEST_DIR_PAYLOAD = ListReader.getSingleton(BaseDir.getEmpty("test"));

	private static final String SERVER_ADDR = "http://localhost:7979/";

	private static final long TEST_TIMEOUT = 5 * 60 * 1000;

	private static int testCount;

	private Path tempDir;

	private Waiter waiter;

	private Server server;

	private EndpointImpl ep;

	private Client client;

	// @BeforeClass
	// public static void beforeClass() {
	// BasicConfigurator.configure();
	// Logger.getRootLogger().setLevel(Level.INFO);
	// }

	@Before
	public void before() throws IOException {
		testCount++;
		tempDir = Files.createTempDirectory("file-transfer-tests");
		waiter = new Waiter();
	}

	@After
	public void after() throws IOException {
		stopServer();
		stopClient();
		if (tempDir != null) {
			PathUtils.deleteWithChildren(tempDir);
			tempDir = null;
		}
		waiter = null;
	}

	public static String getCurrentAddress() {
		return SERVER_ADDR + testCount;
	}

	public void startServer(ServerConfig cfg) {
		Validate.isTrue(server == null);

		server = FileTransfer.createServer(cfg);
		ep = server.getEndpointFactory().createCxf(BusFactory.getThreadDefaultBus());
		ep.publish(getCurrentAddress());

		server.start();
	}

	public void stopServer() {
		if (ep != null) {
			ep.stop();
			ep = null;
		}
		if (server != null) {
			server.stop();
			server = null;
		}
	}

	public void startClient(ClientConfig cfg) {
		Validate.isTrue(client == null);

		client = FileTransfer.createClient(cfg);

		client.start();
	}

	public void stopClient() {
		if (client != null) {
			client.stop();
			client = null;
		}
	}

	@Test
	public void testFolderUpload() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), TEST_DIR_PAYLOAD, waiter,
				TransferState.FINISHED);

		Transfer transfer = client.upload(request);

		waiter.await(TEST_TIMEOUT, 2);

		TransferStatus cts = transfer.getStatus();
		Assert.assertTrue(cts.getState() == TransferState.FINISHED);
		Assert.assertTrue(cts.getTransferedSize() == 0);

		// store will have status only after server stop (or 60s inactivity)
		Assert.assertTrue(scfg.getStatusStorage().getTransferStatus(uth.getLastTransferId()) == null);

		server.stop();

		// test storage status after server stopped
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(uth.getLastTransferId());
		Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
		Assert.assertTrue(sts.getTransferedSize() == 0);
		Assert.assertTrue(sts.getTransferedSeqNum() == 1);
	}

	@Test
	public void testInactiveTransfer() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FAILED);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		scfg.setInactiveTimeout(2); // short enough to get terminated
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), TEST_DIR_PAYLOAD, waiter,
				TransferState.FAILED) {
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
	public void testMaxFrameSizeUpload() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		ccfg.setMaxFrameSize(70); // 70B
		startClient(ccfg);

		ListReader lr = new ListReader(3);
		lr.addItem(new MemoryFile("1.txt", new byte[0], 0)); // empty
		lr.addItem(new MemoryFile("2.txt", new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45 }, 0)); // 5 bytes
		lr.addItem(new GeneratedFile("3.txt", 10, 0)); // 10B which overlaps first frame by 5 bytes
		BaseDir dir = new BaseDir("test", lr);

		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), ListReader.getSingleton(dir), waiter,
				TransferState.FINISHED);

		client.upload(request);

		waiter.await(TEST_TIMEOUT, 2);

		server.stop();

		// test storage status after server stopped
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(uth.getLastTransferId());
		Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
		Assert.assertTrue(sts.getTransferedSize() == 15);
		Assert.assertTrue(sts.getTransferedSeqNum() == 3);
	}

	@Test
	public void testMaxFrameBlocksUpload() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandlerImpl createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		ccfg.setMaxFrameBlocks(5); // 3 directories = 6 blocks, must be 2 frames
		startClient(ccfg);

		ListReader lr = new ListReader(3);
		lr.addItem(BaseDir.getEmpty("1"));
		lr.addItem(BaseDir.getEmpty("2"));
		lr.addItem(BaseDir.getEmpty("3"));
		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), lr, waiter, TransferState.FINISHED);

		client.upload(request);

		waiter.await(TEST_TIMEOUT, 2);

		server.stop();

		// test storage status after server stopped
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(uth.getLastTransferId());
		Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
		Assert.assertTrue(sts.getTransferedSize() == 0);
		Assert.assertTrue(sts.getTransferedSeqNum() == 2);
	}

	@Test
	public void testInvalidChecksum() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FAILED);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		MemoryFile file = new MemoryFile("1.txt", new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45 }, 0); // ABCDE
		// valid checksum
		byte[] chksm = DatatypeConverter.parseHexBinary(
				"9989A8FCBC29044B5883A0A36C146FE7415B1439E995B4D806EA0AF7DA9CA4390EB92A604B3ECFA3D75F9911C768FBE2AECC59EFF1E48DCAECA1957BDDE01DFB");
		// invalid checksum
		chksm[0] = 0x00;
		file.setChecksum(chksm);

		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), ListReader.getSingleton(file), waiter,
				TransferState.FAILED);

		client.upload(request);

		waiter.await(TEST_TIMEOUT, 2);
	}

	@Test
	public void testMixedContentUpload() throws TimeoutException, InterruptedException {
		int blockMax = 5;

		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		ccfg.setMaxFrameBlocks(blockMax);
		startClient(ccfg);

		Pair<Collection<SourceItem>, Integer> pair = createMixedContentWithoutData(3, blockMax);
		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), new ListReader(pair.getLeft()), waiter,
				TransferState.FINISHED);

		client.upload(request);

		waiter.await(TEST_TIMEOUT, 2);

		server.stop();

		// test storage status after server stopped
		int blockCount = pair.getRight() / blockMax * 2;
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(uth.getLastTransferId());
		Assert.assertTrue(sts.getTransferedSeqNum() == blockCount);
		Assert.assertTrue(sts.getTransferedSize() == 0);
	}

	@Test
	public void testInvalidFrameUpload() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, null, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FAILED);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		client = new ClientImpl(ccfg) {
			@Override
			public Transfer upload(UploadRequest request) {
				UploadTransfer transfer = new UploadTransfer(request, config, service) {
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
						OperationResult sos = so.execute();
						if (sos.getType() != Type.SUCCESS) {
							operationFailed(sos);
							return false;
						}
						return true;
					}
				};
				executor.addTask(transfer);
				return transfer;
			}
		};

		client.start();

		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), ListReader.EMPTY, waiter,
				TransferState.FAILED);

		client.upload(request);

		waiter.await(TEST_TIMEOUT, 2);
	}

	@Test
	public void testUploadRequestResponse() throws TimeoutException, ParserConfigurationException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, request, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

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

		UploadRequestImpl request = new UploadRequestImpl(req, ListReader.EMPTY, waiter, TransferState.FINISHED) {
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
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(uth.getLastTransferId());
		Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
		Assert.assertTrue(sts.getResponse().getId().equals("id"));
		Assert.assertTrue(sts.getResponse().getBinData()[0] == 3);
		Assert.assertTrue(sts.getResponse().getXmlData().getAnies().get(0).getTagName().equals("el"));
	}

	@Test
	public void testFolderDownload() throws TimeoutException, InterruptedException {
		DwnldTransferHandler dth = new DwnldTransferHandler() {
			@Override
			protected DownloadHandler createDownload(String transferId, GenericDataType request) {
				return new DwnldHandlerImpl(transferId, null, request.getId(), TEST_DIR_PAYLOAD, server, waiter,
						com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(dth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FINISHED);

		Transfer transfer = client.download(request);

		waiter.await(TEST_TIMEOUT, 2);

		TransferStatus cts = transfer.getStatus();
		Assert.assertTrue(cts.getState() == TransferState.FINISHED);
		Assert.assertTrue(cts.getTransferedSize() == 0);

		// store will have status only after server stop (or 60s inactivity)
		Assert.assertTrue(scfg.getStatusStorage().getTransferStatus(dth.getLastTransferId()) == null);

		server.stop();

		// test storage status after server stopped
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(dth.getLastTransferId());
		Assert.assertTrue(sts.getState() == com.lightcomp.ft.server.TransferState.FINISHED);
		Assert.assertTrue(sts.getTransferedSize() == 0);
		Assert.assertTrue(sts.getTransferedSeqNum() == 1);
	}

	@Test
	public void testMixedContentDownload() throws TimeoutException, InterruptedException {
		int blockMax = 5;
		Pair<Collection<SourceItem>, Integer> pair = createMixedContentWithoutData(3, blockMax);

		DwnldTransferHandler dth = new DwnldTransferHandler() {
			@Override
			protected DownloadHandler createDownload(String transferId, GenericDataType request) {
				return new DwnldHandlerImpl(transferId, null, request.getId(), new ListReader(pair.getLeft()), server,
						waiter, com.lightcomp.ft.server.TransferState.FINISHING);
			}
		};
		ServerConfig scfg = prepareServerConfig(dth);
		scfg.setMaxFrameBlocks(blockMax);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FINISHED);

		client.download(request);

		waiter.await(TEST_TIMEOUT, 2);

		server.stop();

		// test storage status after server stopped
		int blockCount = pair.getRight() / blockMax * 2;
		com.lightcomp.ft.server.TransferStatus sts = scfg.getStatusStorage().getTransferStatus(dth.getLastTransferId());
		Assert.assertTrue(sts.getTransferedSeqNum() == blockCount);
		Assert.assertTrue(sts.getTransferedSize() == 0);
	}

	@Test
	public void testInvalidClientSeqNumDownload() throws TimeoutException, InterruptedException {
		DwnldTransferHandler dth = new DwnldTransferHandler() {
			@Override
			protected DownloadHandler createDownload(String transferId, GenericDataType request) {
				return new DwnldHandlerImpl(transferId, null, request.getId(), TEST_DIR_PAYLOAD, server, waiter,
						com.lightcomp.ft.server.TransferState.FAILED);
			}
		};
		ServerConfig scfg = prepareServerConfig(dth);
		scfg.setMaxFrameBlocks(1); // download 1 folder -> 2 frames
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		client = new ClientImpl(ccfg) {
			@Override
			public synchronized void start() {
				super.start();
				// add out intercepter which will break frame sequence on client
				org.apache.cxf.endpoint.Client client = ClientProxy.getClient(service);
				client.getOutInterceptors().add(new OutMessageInterceptor() {
					@Override
					protected void handleMessage(Object obj) {
						if (obj instanceof ReceiveRequest) {
							ReceiveRequest rr = (ReceiveRequest) obj;
							if (rr.getFrameSeqNum() == 2) {
								rr.setFrameSeqNum(3);
								waiter.resume();
							}
						}
					}
				});
			}
		};
		client.start();

		DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FAILED);

		client.download(request);

		waiter.await(TEST_TIMEOUT, 3);
	}

	@Test
	public void testInvalidClientSeqNumUpload() throws TimeoutException, InterruptedException {
		UploadTransferHandler uth = new UploadTransferHandler(tempDir) {
			@Override
			protected UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request) {
				return new UploadHandlerImpl(transferId, request, request.getId(), uploadDir, server, waiter,
						com.lightcomp.ft.server.TransferState.FAILED);
			}
		};
		ServerConfig scfg = prepareServerConfig(uth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		ccfg.setMaxFrameBlocks(1); // download 1 folder -> 2 frames
		client = new ClientImpl(ccfg) {
			@Override
			public synchronized void start() {
				super.start();
				// add out intercepter which will break frame sequence on client
				org.apache.cxf.endpoint.Client client = ClientProxy.getClient(service);
				client.getOutInterceptors().add(new OutMessageInterceptor() {
					@Override
					protected void handleMessage(Object obj) {
						if (obj instanceof SendRequest) {
							SendRequest sr = (SendRequest) obj;
							if (sr.getFrame().getSeqNum() == 2) {
								sr.getFrame().setSeqNum(3);
								waiter.resume();
							}
						}
					}
				});
			}
		};
		client.start();

		UploadRequestImpl request = new UploadRequestImpl(createReqData("req"), TEST_DIR_PAYLOAD, waiter,
				TransferState.FAILED);

		client.upload(request);

		waiter.await(TEST_TIMEOUT, 3);
	}

	@Test
	public void testInvalidServerSeqNumDownload() throws TimeoutException, InterruptedException {
		// client must abort server when invalid frame number received
		DwnldTransferHandler dth = new DwnldTransferHandler() {
			@Override
			protected DownloadHandler createDownload(String transferId, GenericDataType request) {
				return new DwnldHandlerImpl(transferId, null, request.getId(), TEST_DIR_PAYLOAD, server, waiter,
						com.lightcomp.ft.server.TransferState.ABORTED);
			}
		};
		ServerConfig scfg = prepareServerConfig(dth);
		scfg.setMaxFrameBlocks(1); // download 1 folder -> 2 frames
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		ep.getOutInterceptors().add(new OutMessageInterceptor() {
			@Override
			protected void handleMessage(Object obj) {
				if (obj instanceof Frame) {
					Frame f = (Frame) obj;
					if (f.getSeqNum() == 2) {
						f.setSeqNum(3);
						waiter.resume();
					}
				}
			}
		});

		DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FAILED);

		client.download(request);

		waiter.await(TEST_TIMEOUT, 3);
	}

	@Test
	public void testClientCancelDownload() throws TimeoutException, TransferException, InterruptedException {
		DwnldTransferHandler dth = new DwnldTransferHandler() {
			@Override
			protected DownloadHandler createDownload(String transferId, GenericDataType request) {
				return new DwnldHandlerImpl(transferId, null, request.getId(), TEST_DIR_PAYLOAD, server, waiter,
						com.lightcomp.ft.server.TransferState.ABORTED);
			}
		};
		ServerConfig scfg = prepareServerConfig(dth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.CANCELED) {
			@Override
			public void onTransferProgress(TransferStatus status) {
				super.onTransferProgress(status);
				try {
					transfer.cancel();
				} catch (TransferException e) {
					waiter.fail(e);
				}
			}
		};

		client.download(request);

		waiter.await(TEST_TIMEOUT, 2);
	}

	@Test
	public void testServerCancelDownload() throws TimeoutException, TransferException, InterruptedException {
		DwnldTransferHandler dth = new DwnldTransferHandler() {
			@Override
			protected DownloadHandler createDownload(String transferId, GenericDataType request) {
				return new DwnldHandlerImpl(transferId, null, request.getId(), TEST_DIR_PAYLOAD, server, waiter,
						com.lightcomp.ft.server.TransferState.ABORTED) {
					@Override
					public void onTransferProgress(com.lightcomp.ft.server.TransferStatus status) {
						super.onTransferProgress(status);
						try {
							server.cancelTransfer(transferId);
						} catch (TransferException e) {
							waiter.fail(e);
						}
					}
				};
			}
		};
		ServerConfig scfg = prepareServerConfig(dth);
		startServer(scfg);

		ClientConfig ccfg = prepareClientConfig();
		startClient(ccfg);

		DwnldRequestImpl request = new DwnldRequestImpl(createReqData("req"), tempDir, waiter, TransferState.FAILED);

		client.download(request);

		waiter.await(TEST_TIMEOUT, 2);
	}

	/**
	 * Prepares configuration with StatusStorageImpl and 60s inactive timeout.
	 */
	public static ServerConfig prepareServerConfig(TransferHandler transferHandler) {
		StatusStorageImpl ss = new StatusStorageImpl();
		ServerConfig cfg = new ServerConfig(transferHandler, ss);
		cfg.setInactiveTimeout(60);
		return cfg;
	}

	/**
	 * Prepares configuration with current address and 2s recovery delay.
	 */
	public static ClientConfig prepareClientConfig() {
		ClientConfig cfg = new ClientConfig(getCurrentAddress());
		cfg.setRecoveryDelay(2);
		return cfg;
	}

	public static GenericDataType createReqData(String id) {
		GenericDataType gd = new GenericDataType();
		gd.setId(id);
		return gd;
	}

	public static Pair<Collection<SourceItem>, Integer> createMixedContentWithoutData(int depth, int size) {
		List<SourceItem> items = new ArrayList<>(size);
		int count = size;
		for (int i = 1; i <= size; i++) {
			String cnt = Integer.toString(i);
			if (i % 2 == 0) {
				ListReader lr = ListReader.EMPTY;
				if (depth > 0) {
					Pair<Collection<SourceItem>, Integer> pair = createMixedContentWithoutData(depth - 1, size);
					lr = new ListReader(pair.getLeft());
					count += pair.getRight();
				}
				items.add(new BaseDir(cnt, lr));
			} else {
				MemoryFile file = new MemoryFile(cnt, new byte[0], 0);
				items.add(file);
			}
		}
		return Pair.of(items, count);
	}
}
