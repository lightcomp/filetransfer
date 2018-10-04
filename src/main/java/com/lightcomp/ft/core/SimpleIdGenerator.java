package com.lightcomp.ft.core;

import java.util.concurrent.atomic.AtomicInteger;

public class SimpleIdGenerator implements TransferIdGenerator {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String generateId() {
        int cnt = counter.incrementAndGet();
        long ms = System.currentTimeMillis();
        // build unique string id
        return new StringBuilder().append(cnt).append('-').append(ms).toString();
    }
}
