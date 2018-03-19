package com.lightcomp.ft.sender.impl;

import java.util.Map;

import javax.xml.ws.BindingProvider;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.sender.SenderConfig;
import com.lightcomp.ft.sender.SenderService;
import com.lightcomp.ft.sender.Transfer;
import com.lightcomp.ft.sender.TransferRequest;

import cxf.FileTransferService;
import cxf.FileTransferService_Service;

public class SenderServiceImpl implements SenderService {

    private final FileTransferService service;

    private final TaskExecutor taskExecutor;

    private final SenderConfig config;

    public SenderServiceImpl(SenderConfig config) {
        this.service = createService(config);
        this.taskExecutor = new TaskExecutor(config.getThreadPoolSize());
        this.config = config;
    }

    @Override
    public void start() {
        taskExecutor.start();
    }

    @Override
    public Transfer beginTransfer(TransferRequest transferRequest) {
        TransferImpl transfer = new TransferImpl(transferRequest, config, service);
        taskExecutor.addTask(transfer);
        return transfer;
    }

    @Override
    public void stop() {
        taskExecutor.stop();
    }

    private static FileTransferService createService(SenderConfig config) {
        FileTransferService cs = new FileTransferService_Service().getFileTransferService();

        configureAddress(cs, config.getAddress());

        Client client = ClientProxy.getClient(cs);
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();

        // TODO: replace with not deprecated logging
        client.getInInterceptors().add(new LoggingInInterceptor());
        client.getOutInterceptors().add(new LoggingOutInterceptor());

        long timeoutMs = config.getRequestTimeout() * 1000;
        configureTimeout(httpConduit, timeoutMs);

        return cs;
    }

    private static void configureAddress(FileTransferService ftService, String address) {
        BindingProvider bp = (BindingProvider) ftService;
        Map<String, Object> rc = bp.getRequestContext();
        rc.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, address);
    }

    private static void configureTimeout(HTTPConduit httpConduit, long timeout) {
        HTTPClientPolicy cp = new HTTPClientPolicy();
        cp.setConnectionTimeout(timeout);
        cp.setReceiveTimeout(timeout);
        httpConduit.setClient(cp);
    }
}
