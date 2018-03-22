package com.lightcomp.ft.receiver.impl.tasks;

import com.lightcomp.ft.exception.CanceledException;

public interface Task {

    void run() throws CanceledException;
}
