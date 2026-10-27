package com.gathin.tvhome;

import java.util.function.Supplier;
import java.util.Objects;

final class BackgroundSession {
    interface Player {
        void prepare(String source, Events events);
        void start();
        void release();
    }
    interface Events {
        void prepared();
        void failed();
    }
    interface Timer {
        void after(long milliseconds, Runnable action);
        void cancel();
    }
    private final Supplier<Player> factory;
    private final Timer timer;
    private final Runnable still, video, failure;
    private Player player;
    private String source;
    private int generation;
    private boolean active, started, closed;

    BackgroundSession(Supplier<Player> factory, Timer timer, Runnable still,
            Runnable video, Runnable failure) {
        this.factory = factory;
        this.timer = timer;
        this.still = still;
        this.video = video;
        this.failure = failure;
    }
    void activate(String source) {
        if (closed || (active && Objects.equals(this.source, source))) return;
        deactivate();
        active = true;
        this.source = source;
        if (source == null || source.isEmpty()) return;
        int token = generation;
        timer.after(15000, () -> failed(token));
        try {
            player = factory.get();
            player.prepare(source, new Events() {
                @Override public void prepared() {
                    if (token != generation || player == null || started) return;
                    timer.cancel();
                    try {
                        started = true;
                        player.start();
                        if (token == generation && player != null) video.run();
                    } catch (RuntimeException error) { BackgroundSession.this.failed(token); }
                }
                @Override public void failed() { BackgroundSession.this.failed(token); }
            });
        } catch (RuntimeException error) { failed(token); }
    }
    private void failed(int token) {
        if (token != generation) return;
        generation++;
        release();
        still.run();
        failure.run();
    }
    private void release() {
        timer.cancel();
        Player old = player;
        player = null;
        started = false;
        if (old != null) old.release();
    }
    void deactivate() {
        generation++;
        active = false;
        release();
        still.run();
    }
    void close() {
        if (closed) return;
        closed = true;
        deactivate();
    }
}
