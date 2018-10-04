package com.lightcomp.ft.server;

import java.net.URL;

import javax.xml.ws.soap.SOAPBinding;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.jaxws.EndpointImpl;

import com.lightcomp.ft.server.internal.FileTransferServiceImpl;
import com.lightcomp.ft.server.internal.TransferManager;

public class EndpointFactory {

	private final TransferManager transferManager;

	public EndpointFactory(TransferManager transferManager) {
		this.transferManager = transferManager;
	}

	public String getBindingUri() {
		return SOAPBinding.SOAP12HTTP_MTOM_BINDING;
	}

	/**
	 * Returns WSDL location (jar resource).
	 */
	public URL getWsdlLocation() {
		return getClass().getResource("/wsdl/file-transfer-v1.wsdl");
	}

	/**
	 * Returns WS API implementor, suitable for end-point publishing.
	 */
	public Object getImplementor() {
		return new FileTransferServiceImpl(transferManager);
	}

	public EndpointImpl createCxfEndpoint() {
	    String wsdlLocation = getWsdlLocation().toExternalForm();
	    Validate.notEmpty(wsdlLocation);
	    
		return new EndpointImpl(null, getImplementor(), getBindingUri(), wsdlLocation, null);
	}
}
