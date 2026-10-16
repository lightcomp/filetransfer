package com.lightcomp.ft.client.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.Test;

import com.lightcomp.ft.client.ClientConfig;

/**
 * TLS verification of the client connection.
 */
public class ClientTlsTest {

    private static final String ADDRESS = "https://ft.example/ws/ft";

    private static HTTPConduit conduit(ClientConfig config) {
        ClientImpl client = new ClientImpl(config);
        return (HTTPConduit) ClientProxy.getClient(client.service).getConduit();
    }

    @Test
    public void defaultVerifiesCertificate() {
        ClientConfig config = new ClientConfig(ADDRESS);

        assertTrue(config.isTlsVerify());
        assertNull(conduit(config).getTlsClientParameters());
    }

    @Test
    public void skippedVerificationTrustsAnyCertificate() throws Exception {
        ClientConfig config = new ClientConfig(ADDRESS);
        config.setTlsVerify(false);

        TLSClientParameters tls = conduit(config).getTlsClientParameters();

        assertNotNull(tls);
        assertTrue(tls.isDisableCNCheck());
        X509TrustManager trustManager = (X509TrustManager) tls.getTrustManagers()[0];
        // a self-signed certificate is accepted without an exception
        trustManager.checkServerTrusted(new X509Certificate[0], "RSA");
    }
}
