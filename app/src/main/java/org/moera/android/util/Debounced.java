package org.moera.android.util;

import java.util.concurrent.atomic.AtomicInteger;

public class Debounced {

    private final Runnable runnable;
    private final int delay;

    private Thread thread;
    private final AtomicInteger calls = new AtomicInteger(0);
    private final Object lock = new Object();

    public Debounced(Runnable runnable, int delay) {
        this.runnable = runnable;
        this.delay = delay;
    }

    public void execute() {
        synchronized (lock) {
            if (thread == null) {
                calls.set(0);
                thread = new Thread(this::run);
                thread.start();
            } else {
                calls.incrementAndGet();
            }
        }
    }

    private void run() {
        int prevCalls = -1;
        try {
            while (prevCalls != calls.get()) {
                prevCalls = calls.get();
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            // Just exit
        }
        runnable.run();
        synchronized (lock) {
            thread = null;
        }
    }

}
