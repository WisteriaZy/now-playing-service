package com.widdit.nowplaying.component;

import java.util.Objects;
import java.util.concurrent.*;

/** One in-flight load and one replaceable latest request, never an unbounded task queue. */
public final class LatestOnlyLoader<T> implements AutoCloseable {
    public static final class LoadState<T> {
        public final boolean done;
        public final T value;
        private LoadState(boolean done, T value) { this.done = done; this.value = value; }
    }
    private static final class Request<T> {
        final String key; final Callable<T> load; final long since = System.nanoTime();
        T value; boolean done;
        Request(String key, Callable<T> load) { this.key = key; this.load = load; }
    }
    private final ScheduledExecutorService worker;
    private final long debounceNanos;
    private final Runnable changed;
    private Request<T> latest;
    public LatestOnlyLoader(String name, long debounceMs, Runnable changed) {
        this.changed = changed;
        debounceNanos = TimeUnit.MILLISECONDS.toNanos(debounceMs);
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name); t.setDaemon(true); return t;
        });
        worker.scheduleWithFixedDelay(this::tick, 50, 50, TimeUnit.MILLISECONDS);
    }
    public synchronized void request(String key, Callable<T> load) {
        if (latest == null || !Objects.equals(key, latest.key)) latest = new Request<>(key, load);
    }
    public synchronized T get(String key) { return latest != null && Objects.equals(key, latest.key) ? latest.value : null; }
    /** Completion and value must be observed together to avoid a false empty result at completion. */
    public synchronized LoadState<T> read(String key) {
        return latest != null && Objects.equals(key, latest.key)
                ? new LoadState<>(latest.done, latest.value) : new LoadState<>(false, null);
    }
    public synchronized void clear() { latest = null; }
    private void tick() {
        Request<T> request;
        synchronized (this) {
            request = latest;
            if (request == null || request.done || System.nanoTime() - request.since < debounceNanos) return;
        }
        T result = null;
        try { result = request.load.call(); } catch (Exception ignored) { }
        synchronized (this) {
            if (latest != request) return; // includes A -> B -> A and disconnect/reconnect
            request.value = result; request.done = true;
        }
        // Notifications read current state, not the completed request's payload.
        try { changed.run(); } catch (Exception ignored) { }
    }
    @Override public void close() { clear(); worker.shutdownNow(); }
}
