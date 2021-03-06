package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.exception.TransferException;

public interface BlockProcessor {

    default void receive(RecvContext ctx) throws TransferException {
        throw new UnsupportedOperationException();
    }
}
