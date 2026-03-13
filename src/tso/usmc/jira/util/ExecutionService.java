package tso.usmc.jira.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Centralized service for background task execution.
 * Replaces ad-hoc 'new Thread()' calls with a managed thread pool.
 */
public class ExecutionService {
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    /**
     * Executes a task in the background thread pool.
     */
    public static Future<?> submit(Runnable task) {
        return pool.submit(task);
    }

    /**
     * Shuts down the thread pool gracefully.
     */
    public static void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
    }
}
