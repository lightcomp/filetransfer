package com.lightcomp.ft.sender.impl.phase;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.sender.TransferState;

/**
 * Sender transfer phase.
 */
public interface Phase {

    /**
     * Process phase.
     */
    void process() throws CanceledException;

    /**
     * @return Returns next phase which will continue in transfer.
     */
    Phase getNextPhase();

    /**
     * @return Returns state which will be update if finished normally.
     */
    TransferState getNextState();
}
