package com.lightcomp.ft.client.internal;

import javax.xml.ws.soap.SOAPBinding;

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
        JaxWsProxyFactoryBean fb = new JaxWsProxyFactoryBean();
        fb.setAddress(config.getAddress());
        // enable logging if needed
        if (config.isSoapLogging()) {
            fb.getFeatures().add(new LoggingFeature());
        }
        // set SOAP 1.2 with MTOM
        fb.setBindingId(SOAPBinding.SOAP12HTTP_MTOM_BINDING);
        // create HTTP policy with timeouts
        long ms = config.getRequestTimeout() * 1000;
        HTTPClientPolicy cp = new HTTPClientPolicy();
        cp.setConnectionTimeout(ms);
        cp.setReceiveTimeout(ms);
        // create service 
        FileTransferService service = fb.create(FileTransferService.class);
        // set HTTP policy
        org.apache.cxf.endpoint.Client client = ClientProxy.getClient(service);
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        httpConduit.setClient(cp);

        return service;
    }
}
