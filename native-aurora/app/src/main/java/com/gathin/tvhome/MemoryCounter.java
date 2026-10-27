package com.gathin.tvhome;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class MemoryCounter {
    interface Reader { MemoryReading read(); }
    interface Scheduler { void after(long delayMs, Runnable task); void cancel(); }
    private final Reader reader;
    private final Scheduler timer;
    private final Executor worker, main;
    private final Consumer<MemoryReading> display;
    private boolean active, enabled, reading, closed;
    private int generation;
    MemoryCounter(Reader reader, Scheduler timer, Executor worker, Executor main,
            Consumer<MemoryReading> display) {
        this.reader = reader;
        this.timer = timer;
        this.worker = worker;
        this.main = main;
        this.display = display;
    }
    void setActive(boolean active) {
        if (this.active == active || closed) return;
        this.active = active;
        changed();
    }
    void setEnabled(boolean enabled) {
        if (this.enabled == enabled || closed) return;
        this.enabled = enabled;
        changed();
    }
    private boolean eligible() { return !closed && active && enabled; }
    private void changed() {
        generation++;
        timer.cancel();
        if (eligible()) sample();
    }
    private void sample() {
        if (!eligible() || reading) return;
        reading = true;
        int token = generation;
        worker.execute(() -> {
            MemoryReading result;
            try { result = reader.read(); }
            catch (RuntimeException unavailable) { result = null; }
            MemoryReading completed = result;
            main.execute(() -> {
                reading = false;
                if (!eligible()) return;
                if (token != generation) { sample(); return; }
                display.accept(completed);
                timer.after(5000, this::sample);
            });
        });
    }
    void close() {
        closed = true;
        generation++;
        timer.cancel();
    }
}
