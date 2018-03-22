package com.lightcomp.ft.receiver.impl.tasks;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.xsd.v1.FileTransfer;

public class BeginTask implements Task {

    public BeginTask(FileTransfer fileTransfer) {
        // TODO Auto-generated constructor stub
        // ChecksumType cht = ChecksumType.fromValue(fileTransfer.getChecksumType());
    }

    @Override
    public void run() throws CanceledException {
        // TODO Auto-generated method stub
    }
}
