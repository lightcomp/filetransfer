package com.lightcomp.ft.common;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

    private enum State {
        RUNNING, STOPPING, TERMINATED
    }

    private final LinkedList<Runnable> taskQueue = new LinkedList<>();

    private final List<Runnable> processingTasks;

    private final ExecutorService executorService;

    private final int threadPoolSize;

    private final String threadNamePostfix;

    private State state = State.TERMINATED;

    public TaskExecutor(int threadPoolSize, String threadNamePostfix) {
        Validate.isTrue(threadPoolSize > 0);

        this.processingTasks = new ArrayList<>(threadPoolSize);
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.threadPoolSize = threadPoolSize;
        this.threadNamePostfix = threadNamePostfix;
    }

    /**
     * Start async queue processing.
     */
    public synchronized void start() {
        Validate.isTrue(state == State.TERMINATED);

        state = State.RUNNING;
        Thread managerThread = new Thread(this::runManager,
                "TaskExecutorManager-" + threadNamePostfix + "-" + System.identityHashCode(this));
        managerThread.start();
    }

    /**
     * Caller stop async queue processing. This operation will block caller thread until task executor
     * does not terminate.
     */
    public synchronized void stop() {
        if (state == State.TERMINATED) {
            return;
        }
        state = State.STOPPING;
        // notify manager thread about stopping
        notifyAll();
        // wait for termination
        while (state != State.TERMINATED) {
            try {
                wait(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Adds task to queue for processing.
     */
    public synchronized void addTask(Runnable task) {
        Validate.isTrue(state == State.RUNNING);
        Validate.notNull(task);

        taskQueue.addLast(task);
        // notify manager thread about new task
        if (processingTasks.size() < threadPoolSize) {
            notifyAll();
        }
    }

    private synchronized void runManager() {
        while (state == State.RUNNING) {
            // wait if maximum of processing task reached or taskQueue is empty
            if (processingTasks.size() >= threadPoolSize || taskQueue.isEmpty()) {
                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Task executor manager waiting ...");
                    }
                    wait();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Task executor manager woke up, processingTasks={}, queuedTasks={}, state={}",
                                processingTasks.size(), taskQueue.size(), state);
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
                continue;
            }
            // execute next task from queue
            Runnable task = taskQueue.removeFirst();
            processingTasks.add(task);
            executorService.submit(() -> {
                try {
                    task.run();
                } finally {
                    taskFinished(task);
                }
            });
        }
        stopManager();
    }

    private void stopManager() {
        // wait for currently processing tasks
        while (processingTasks.size() > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Task executor stopped");
        }
        state = State.TERMINATED;
        // notify stopping thread about termination
        notifyAll();
    }

    private synchronized void taskFinished(Runnable task) {
        processingTasks.remove(task);
        // notify manager thread about ended task
        notifyAll();
    }
}
