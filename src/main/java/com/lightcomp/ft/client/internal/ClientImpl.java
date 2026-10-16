package com.lightcomp.ft.client.internal;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import com.lightcomp.ft.client.Client;
import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.server.EndpointFactory;
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

        // set WSDL location
        String wsdlLocation = EndpointFactory.getWsdlLocation().toExternalForm();
        fb.setWsdlLocation(wsdlLocation);

        // enable MTOM
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(Message.MTOM_ENABLED, Boolean.TRUE);
        fb.setProperties(props);

        // enable logging if requested
        if (config.isSoapLogging()) {
            LoggingFeature lf = new LoggingFeature();
            lf.setPrettyLogging(true);
            fb.getFeatures().add(lf);
        }

        // create service 
        FileTransferService service = fb.create(FileTransferService.class);

        // prepare HTTP policy with timeouts
        HTTPConduit conduit = (HTTPConduit) ClientProxy.getClient(service).getConduit();
        
        if (config.getAuthorization() != null) {
            conduit.setAuthorization(config.getAuthorization().toPolicy());
        }
        
        HTTPClientPolicy policy = new HTTPClientPolicy();
        long msTimeout = config.getRequestTimeout() * 1000L;
        policy.setConnectionTimeout(msTimeout);
        policy.setReceiveTimeout(msTimeout);
        conduit.setClient(policy);

        if (!config.isTlsVerify()) {
            conduit.setTlsClientParameters(createTrustAllTlsParameters());
        }

        return service;
    }

    /**
     * TLS parameters trusting any server certificate and skipping the host name check.
     */
    static TLSClientParameters createTrustAllTlsParameters() {
        TLSClientParameters tls = new TLSClientParameters();
        tls.setTrustManagers(new TrustManager[] { new TrustAllManager() });
        tls.setDisableCNCheck(true);
        return tls;
    }

    private static final class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
