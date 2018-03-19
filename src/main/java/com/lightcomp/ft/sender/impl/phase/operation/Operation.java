package com.lightcomp.ft.sender.impl.phase.operation;

import com.lightcomp.ft.exception.CanceledException;

import cxf.FileTransferException;

public interface Operation {

	void send() throws FileTransferException, CanceledException;

	Operation createRetry();
}
