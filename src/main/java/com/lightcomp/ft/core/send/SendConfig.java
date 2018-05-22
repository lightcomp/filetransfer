package com.lightcomp.ft.core.send;

import com.lightcomp.ft.common.Checksum.Algorithm;

public interface SendConfig {

    /**
     * @return Maximum frame size in bytes. Must be at least equal to checksum byte length.
     */
    long getMaxFrameSize();

    /**
     * @return Maximum number of blocks in one frame.
     */
    int getMaxFrameBlocks();

    /**
     * @return Checksum algorithm.
     */
    Algorithm getChecksumAlg();
}
