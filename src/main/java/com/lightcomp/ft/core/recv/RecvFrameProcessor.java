package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class RecvFrameProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecvFrameProcessor.class);

    private final Frame frame;

    private final RecvContext recvCtx;

    private Path dataFile;

    private long dataPos;

    private ReadableByteChannel dataChannel;

    public RecvFrameProcessor(Frame frame, RecvContext recvCtx) {
        this.frame = frame;
        this.recvCtx = recvCtx;
    }

    public int getSeqNum() {
        return frame.getSeqNum();
    }

    public boolean isLast() {
        return Boolean.TRUE.equals(frame.isLast());
    }

    public void transfer(Path workDir) {
        Validate.isTrue(dataFile == null);
        try {
            dataFile = Files.createTempFile(workDir, Integer.toString(frame.getSeqNum()), null);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to create temporary file for frame data")
                    .addParam("frameSeqNum", frame.getSeqNum()).setCause(e).build();
        }
        try (InputStream is = frame.getData().getInputStream()) {
            // copy whole data stream to data file
            long length = Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
            // copied length must match with specified data size
            if (length != frame.getDataSize()) {
                cleanUp(); // delete temp file
                throw TransferExceptionBuilder.from("Frame size does not match data length")
                        .addParam("frameSeqNum", frame.getSeqNum()).addParam("frameSize", frame.getDataSize())
                        .addParam("dataLength", length).build();
            }
        } catch (IOException e) {
            cleanUp(); // delete temp file
            throw TransferExceptionBuilder.from("Failed to transfer frame data").addParam("frameSeqNum", frame.getSeqNum())
                    .setCause(e).build();
        }
    }

    public void process() {
        int blockNum = 1;
        try (ReadableByteChannel rbch = openDataChannel()) {
            // set input channel for receive context
            recvCtx.setInputChannel(rbch);
            // process all blocks
            for (FrameBlock b : frame.getBlocks().getDesAndFdsAndFes()) {
                b.receive(recvCtx);
                blockNum++;
            }
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to process frame block").addParam("frameSeqNum", frame.getSeqNum())
                    .addParam("blockNum", blockNum).setCause(t).build();
        } finally {
            // release input channel
            recvCtx.setInputChannel(null);
            // close and delete data
            cleanUp();
        }
        validate();
    }

    private ReadableByteChannel openDataChannel() {
        Validate.isTrue(dataChannel == null);
        try {
            dataChannel = Files.newByteChannel(dataFile, StandardOpenOption.READ);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open temporary frame data").addParam("frameSeqNum", frame.getSeqNum())
                    .addParam("dataFile", dataFile).setCause(e).build();
        }
        return new ReadableByteChannel() {
            @Override
            public boolean isOpen() {
                return dataChannel.isOpen();
            }

            @Override
            public void close() throws IOException {
                dataChannel.close();
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int n = dataChannel.read(dst);
                dataPos += n;
                return n;
            }
        };
    }

    private void cleanUp() {
        try {
            if (dataChannel != null) {
                dataChannel.close();
                dataChannel = null;
            }
            Files.delete(dataFile);
        } catch (Throwable t) {
            TransferExceptionBuilder.from("Failed to clear frame temporary data").addParam("frameSeqNum", frame.getSeqNum())
                    .setCause(t).log(logger);
        }
    }

    private void validate() {
        long dataSize = frame.getDataSize();
        if (dataSize != dataPos) {
            throw TransferExceptionBuilder.from("Not all data from stream processed").addParam("frameSeqNum", frame.getSeqNum())
                    .addParam("dataSize", dataSize).addParam("dataPosition", dataPos).build();
        }
        if (Boolean.TRUE.equals(frame.isLast())) {
            Path currDir = recvCtx.getCurrentDir();
            if (currDir != null) {
                throw TransferExceptionBuilder.from("Directory inconsistency, after last frame the directory still remains open")
                        .addParam("frameSeqNum", frame.getSeqNum()).addParam("remainingDir", currDir).build();
            }
            Path currFile = recvCtx.getCurrentFile();
            if (currFile != null) {
                throw TransferExceptionBuilder.from("File inconsistency, after last frame the file still remains open")
                        .addParam("frameSeqNum", frame.getSeqNum()).addParam("remainingFile", currFile).build();
            }
        }
    }
}
