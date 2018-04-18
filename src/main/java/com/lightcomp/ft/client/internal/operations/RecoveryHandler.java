package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.exception.CanceledException;

public interface RecoveryHandler {

    void waitBeforeRecovery() throws CanceledException;
}
