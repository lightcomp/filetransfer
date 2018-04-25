package com.lightcomp.ft.core.receiver;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.internal.upload.FrameProcessorImpl;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class ReceiveFrameProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FrameProcessorImpl.class);

    private ReceiveContext receiveCtx;

    private final int seqNum;

    private final boolean last;

    private final long dataSize;

    private final Collection<FrameBlock> blocks;

    private InputStream is;

    private long dataPos;

    private ReceiveFrameProcessor(ReceiveContext receiveCtx, int seqNum, boolean last, long dataSize,
            Collection<FrameBlock> blocks) {
        this.receiveCtx = receiveCtx;
        this.seqNum = seqNum;
        this.last = last;
        this.dataSize = dataSize;
        this.blocks = blocks;
    }

    public void process(Path dataFile) {
        prepareDataChannel(dataFile);

        for (FrameBlock b : blocks) {
            b.receive(receiveCtx);
        }
    }

    private void prepareDataChannel(Path dataFile) {
        // TODO Auto-generated method stub

    }

    public static ReceiveFrameProcessor create(ReceiveContext receiveCtx, Frame frame) {
        boolean lastFrame = frame.isLast() != null ? frame.isLast() : false;
        Collection<FrameBlock> frameBlocks = frame.getBlocks().getDesAndFdsAndFes();
        return new ReceiveFrameProcessor(receiveCtx, frame.getSeqNum(), lastFrame, frame.getDataSize(), frameBlocks);
    }
}
