package com.gathin.tvhome;

final class HomeMusicSession implements AutoCloseable {
    interface Backend {
        void prepare(long generation);
        void start();
        void release();
    }

    private final Backend backend;
    private boolean active, pending, started, closed;
    private long generation;

    HomeMusicSession(Backend backend) { this.backend = backend; }

    void setActive(boolean active) {
        if (closed || this.active == active) return;
        this.active = active;
        if (active) {
            pending = true;
            started = false;
            backend.prepare(++generation);
        } else {
            finished(generation);
            generation++;
        }
    }

    void prepared(long generation) {
        if (closed || !active || !pending || started || this.generation != generation) return;
        started = true;
        backend.start();
    }

    void finished(long generation) {
        if (!pending || this.generation != generation) return;
        pending = false;
        backend.release();
    }

    @Override public void close() {
        if (closed) return;
        setActive(false);
        closed = true;
    }
}
