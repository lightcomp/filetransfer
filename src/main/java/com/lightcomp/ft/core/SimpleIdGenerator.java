package com.lightcomp.ft.core;

import java.util.concurrent.atomic.AtomicInteger;

public class SimpleIdGenerator implements TransferIdGenerator {

    private final AtomicInteger counter = new AtomicInteger(0);

    private final StringBuilder sb = new StringBuilder();

    @Override
    public String generateId() {
        int cnt = counter.incrementAndGet();
        long ms = System.currentTimeMillis();
        // build unique string id
        sb.setLength(0);
        return sb.append(cnt).append('-').append(ms).toString();
    }
}
