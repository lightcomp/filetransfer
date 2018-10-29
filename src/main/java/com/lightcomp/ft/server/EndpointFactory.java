package com.lightcomp.ft.server;

import java.net.URL;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.Bus;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.message.Message;

import com.lightcomp.ft.server.internal.FileTransferServiceImpl;
import com.lightcomp.ft.server.internal.TransferManager;

public class EndpointFactory {

    private final TransferManager transferManager;

    private final ServerConfig config;

    public EndpointFactory(TransferManager transferManager, ServerConfig config) {
        this.transferManager = transferManager;
        this.config = config;
    }

    /**
     * Returns WS API implementor, suitable for end-point publishing.
     */
    public Object getImplementor() {
        return new FileTransferServiceImpl(transferManager);
    }

    /**
     * Creates unpublished CXF endpoint.
     */
    public EndpointImpl createCxf(Bus bus) {
        Validate.notNull(bus);

        EndpointImpl ep = new EndpointImpl(bus, getImplementor());

        // set WSDL location
        String wsdlLocation = EndpointFactory.getWsdlLocation().toExternalForm();
        ep.setWsdlLocation(wsdlLocation);
        
        // enable MTOM
        ep.getProperties().put(Message.MTOM_ENABLED, Boolean.TRUE);
        
        // enable logging if requested
        if (config.isSoapLogging()) {
            LoggingFeature lf = new LoggingFeature();
            lf.setPrettyLogging(true);
            ep.getFeatures().add(lf);
        }

        return ep;
    }

    /**
     * Returns WSDL location (jar resource).
     */
    public static URL getWsdlLocation() {
        return EndpointFactory.class.getResource("/wsdl/file-transfer-v1.wsdl");
    }
}
