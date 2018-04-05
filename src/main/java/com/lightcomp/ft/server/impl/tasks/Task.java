package com.lightcomp.ft.server.impl.tasks;

import com.lightcomp.ft.exception.CanceledException;

public interface Task {

    void run() throws CanceledException;
}
