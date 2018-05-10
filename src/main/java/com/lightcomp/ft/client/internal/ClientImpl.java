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

public class ClientImpl implements Client {

    protected final TaskExecutor transferExecutor;

    protected final ClientConfig config;

    protected FileTransferService service;

    public ClientImpl(ClientConfig config) {
        this.transferExecutor = new TaskExecutor(config.getThreadPoolSize());
        this.config = config;
    }

    @Override
    public Transfer upload(UploadRequest request) {
        Validate.isTrue(service != null);

        AbstractTransfer transfer = new UploadTransfer(request, config, service);
        transferExecutor.addTask(transfer);
        return transfer;
    }

    @Override
    public Transfer download(DownloadRequest request) {
        Validate.isTrue(service != null);

        AbstractTransfer transfer = new DownloadTransfer(request, config, service);
        transferExecutor.addTask(transfer);
        return transfer;
    }

    @Override
    public void start() {
        transferExecutor.start();
        service = createService(config);
    }

    @Override
    public void stop() {
        service = null;
        transferExecutor.stop();
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
