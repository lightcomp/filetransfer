package com.lightcomp.ft.client.internal;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

/**
 * File transfer client implementation.
 */
public class ClientImpl implements Client {

	protected final TaskExecutor executor;

	protected final FileTransferService service;

	protected final ClientConfig config;

	public ClientImpl(ClientConfig config) {
		this.executor = new TaskExecutor(config.getThreadPoolSize(), "client");
		this.service = createService(config);
		this.config = config;
	}

	@Override
	public synchronized Transfer upload(UploadRequest request) {
		Validate.isTrue(executor.isRunning());

		AbstractTransfer transfer = new UploadTransfer(request, config, service);
		executor.addTask(transfer);
		return transfer;
	}

	@Override
	public void uploadSync(UploadRequest request) {
		AbstractTransfer transfer = new UploadTransfer(request, config, service);
		transfer.run();
	}

	@Override
	public synchronized Transfer download(DownloadRequest request) {
		Validate.isTrue(executor.isRunning());

		AbstractTransfer transfer = new DownloadTransfer(request, config, service);
		executor.addTask(transfer);
		return transfer;
	}

	@Override
	public void downloadSync(DownloadRequest request) {
		AbstractTransfer transfer = new DownloadTransfer(request, config, service);
		transfer.run();
	}

	@Override
	public synchronized void start() {
		executor.start();
	}

	@Override
	public synchronized void stop() {
		executor.stop();
	}

	private static FileTransferService createService(ClientConfig config) {
		JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
		factory.setAddress(config.getAddress());

		if (config.isSoapLogging()) {
			factory.getFeatures().add(new LoggingFeature());
		}

		FileTransferService service = factory.create(FileTransferService.class);
		org.apache.cxf.endpoint.Client client = ClientProxy.getClient(service);
		HTTPConduit httpConduit = (HTTPConduit) client.getConduit();

		configureTimeout(httpConduit, config);

		return service;
	}

	private static void configureTimeout(HTTPConduit httpConduit, ClientConfig config) {
		long ms = config.getRequestTimeout() * 1000;
		HTTPClientPolicy cp = new HTTPClientPolicy();
		cp.setConnectionTimeout(ms);
		cp.setReceiveTimeout(ms);
		httpConduit.setClient(cp);
	}
}
