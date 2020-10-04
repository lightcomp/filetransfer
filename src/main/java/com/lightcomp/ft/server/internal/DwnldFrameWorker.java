package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.core.send.FrameBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;

public class DwnldFrameWorker implements Runnable {

    private enum State {
        RUNNING, STOPPING, TERMINATED
    }

    private final DwnldTransfer transfer;

    private final FrameBuilder frameBuilder;

    private State state = State.RUNNING;

    public DwnldFrameWorker(DwnldTransfer transfer, FrameBuilder frameBuilder) {
        this.transfer = transfer;
        this.frameBuilder = frameBuilder;
    }

    public synchronized void terminate() {
        if (state == State.TERMINATED) {
            return;
        }
        state = State.STOPPING;
        // wait until terminate
        while (state != State.TERMINATED) {
            try {
                wait(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

	@Override
	public void run() {
		while (true) {
			synchronized (this) {
				if (state != State.RUNNING) {
					break; // stopping worker
				}
			}
			try {
				SendFrameContext frameCtx = frameBuilder.build();
				if (!transfer.frameProcessed(frameCtx)) {
					break; // worker terminated
				}
			} catch (Throwable t) {
				ServerError err = new ServerError("Failed to build download frame", transfer)
						.addParam("seqNum", frameBuilder.getCurrentSeqNum()).setCause(t);
				transfer.frameProcessingFailed(err);
				break; // builder failed
			}
		}
		synchronized (this) {
			state = State.TERMINATED;
			// notify terminating threads
			notifyAll();
		}
	}
}
