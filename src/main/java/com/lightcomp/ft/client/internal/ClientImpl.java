package com.lightcomp.ft.client.internal;

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

public class ClientImpl implements Client {

    private final TaskExecutor taskExecutor;

    private final ClientConfig config;

    private final FileTransferService service;

    public ClientImpl(ClientConfig config) {
        this.taskExecutor = new TaskExecutor(config.getThreadPoolSize());
        this.config = config;
        this.service = createService(config);
    }

    @Override
    public Transfer upload(UploadRequest request) {
        AbstractTransfer transfer = new UploadTransfer(request, config, service);
        taskExecutor.addTask(transfer);
        return transfer;
    }

    @Override
    public Transfer download(DownloadRequest request) {
        // TODO: client download impl
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        taskExecutor.start();
    }

    @Override
    public void stop() {
        taskExecutor.stop();
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

        configureTimeouts(httpConduit, config);

        return service;
    }

    private static void configureTimeouts(HTTPConduit httpConduit, ClientConfig config) {
        long ms = config.getRequestTimeout() * 1000;
        HTTPClientPolicy cp = new HTTPClientPolicy();
        cp.setConnectionTimeout(ms);
        cp.setReceiveTimeout(ms);
        httpConduit.setClient(cp);
    }
}
