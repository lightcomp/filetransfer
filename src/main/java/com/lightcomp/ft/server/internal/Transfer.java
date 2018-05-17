package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public interface Transfer {

    void recvFrame(Frame frame) throws FileTransferException;

    Frame sendFrame(long seqNum) throws FileTransferException;

    GenericDataType finish() throws FileTransferException;

    void abort() throws FileTransferException;
}
