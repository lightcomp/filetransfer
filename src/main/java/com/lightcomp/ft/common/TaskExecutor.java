package com.lightcomp.ft.common;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.Validate;

public class TaskExecutor {

    /**
     * Internal state. Do not change definition order!
     */
    private enum State {
        INIT, RUNNING, STOPPING, TERMINATED
    }

    private final LinkedList<Runnable> taskQueue = new LinkedList<>();

    private final List<Runnable> processingTasks;

    private final ExecutorService executorService;

    private final int threadPoolSize;

    private volatile State state = State.INIT;

    private Thread managerThread;

    public TaskExecutor(int threadPoolSize) {
        Validate.isTrue(threadPoolSize > 0);

        this.processingTasks = new ArrayList<>(threadPoolSize);
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Start async queue processing.
     */
    public synchronized void start() {
        Validate.isTrue(state == State.INIT);

        state = State.RUNNING;
        managerThread = new Thread(this::run, "TaskExecutorManager");
        managerThread.start();
    }

    /**
     * Caller stop async queue processing. This operation will block caller thread
     * until manager thread does not terminate.
     *
     * When terminated no more tasks will be passed to execution.
     */
    public synchronized void stop() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // notify manager thread about stopping
            notify();
            // wait until manager thread does not terminate
            while (state != State.TERMINATED) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Adds task to queue for processing.
     *
     * @return False when task already in queue.
     */
    public synchronized void addTask(Runnable task) {
        Validate.isTrue(state.ordinal() <= State.RUNNING.ordinal());
        Validate.notNull(task);

        taskQueue.addLast(task);
        // notify manager thread about new task
        if (processingTasks.size() < threadPoolSize) {
            notify();
        }
    }

    private synchronized void run() {
        while (state == State.RUNNING) {
            if (processingTasks.size() >= threadPoolSize || taskQueue.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // NOP -> continue
                }
                continue;
            }

            Runnable task = taskQueue.removeFirst();

            processingTasks.add(task);
            executorService.submit(() -> {
                try {
                    task.run();
                } finally {
                    onTaskFinished(task);
                }
            });
        }
        state = State.TERMINATED;
    }

    private synchronized void onTaskFinished(Runnable task) {
        processingTasks.remove(task);
        // notify manager thread about ended task
        if (taskQueue.size() > 0) {
            notify();
        }
    }
}
