package com.lightcomp.ft.core.send;

public interface BlockStreamProvider {

    long getStreamSize();
    
    BlockStream create();
}
