package com.nikohalfkino.rubydung.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** single background thread for world saves, so saves run in order and two never write the same file at once */
public final class SaveQueue {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "save-thread");
        thread.setDaemon(false); // never let the JVM exit mid-write
        return thread;
    });

    private SaveQueue() {
    }

    public static void submit(Runnable job) {
        EXEC.submit(job);
    }

    /** blocks until everything queued so far has been written */
    public static void flush() {
        try {
            EXEC.submit(() -> { }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    /** call once on shutdown, after the final save */
    public static void shutdownAndWait() {
        EXEC.shutdown();
        try {
            EXEC.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
