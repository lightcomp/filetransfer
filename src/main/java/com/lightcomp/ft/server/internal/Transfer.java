package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public interface Transfer {

    void recvFrame(Frame frame) throws FileTransferException;

    Frame sendFrame(int seqNum) throws FileTransferException;

    GenericDataType finish() throws FileTransferException;

    void abort() throws FileTransferException;

    /**
     * Confirmed status represent transfer when is not processing any task (not busy).
     */
    TransferStatus getConfirmedStatus() throws FileTransferException;
}
