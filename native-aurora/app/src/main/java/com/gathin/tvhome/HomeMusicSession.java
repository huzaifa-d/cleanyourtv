package com.gathin.tvhome;

final class HomeMusicSession implements AutoCloseable {
    interface Backend {
        void prepare(long generation, int position);
        void start();
        Integer playingPosition();
        void release();
    }

    private final Backend backend;
    private boolean active, pending, started, closed;
    private long generation;
    private int position;

    HomeMusicSession(Backend backend) { this.backend = backend; }

    void setActive(boolean active) {
        if (closed || this.active == active) return;
        this.active = active;
        if (active) {
            pending = true;
            started = false;
            backend.prepare(++generation, position);
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
        if (started) {
            Integer current = backend.playingPosition();
            if (current != null) position = current;
        }
        pending = false;
        backend.release();
    }

    void completed(long generation) {
        if (!pending || this.generation != generation) return;
        position = 0;
        started = false;
        finished(generation);
    }

    @Override public void close() {
        if (closed) return;
        setActive(false);
        closed = true;
    }
}
