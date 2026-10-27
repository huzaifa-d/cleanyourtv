package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;

public class BackgroundSessionTest {
    static final class FakePlayer implements BackgroundSession.Player {
        BackgroundSession.Events events;
        int starts, releases;
        float speed;
        boolean throwPrepare, throwStart;
        @Override public void prepare(String source, float speed, BackgroundSession.Events events) {
            this.events = events;
            this.speed = speed;
            if (throwPrepare) throw new IllegalStateException();
        }
        @Override public void start() {
            if (throwStart) throw new IllegalStateException();
            starts++;
        }
        @Override public void release() { releases++; }
    }
    static final class Factory implements Supplier<BackgroundSession.Player> {
        final List<FakePlayer> players = new ArrayList<>();
        boolean throwPrepare;
        @Override public FakePlayer get() {
            if (!players.isEmpty()) assertEquals(1, players.get(players.size() - 1).releases);
            FakePlayer player = new FakePlayer();
            player.throwPrepare = throwPrepare;
            players.add(player);
            return player;
        }
    }
    static final class Timer implements BackgroundSession.Timer {
        Runnable task;
        @Override public void after(long delay, Runnable task) { this.task = task; }
        @Override public void cancel() { task = null; }
        void fire() { Runnable pending = task; task = null; if (pending != null) pending.run(); }
    }
    private final Factory factory = new Factory();
    private final Timer timer = new Timer();
    private int failures, videos;
    private final BackgroundSession session = new BackgroundSession(factory, timer,
            () -> { }, () -> videos++, () -> failures++);

    @Test public void oldPreparedEventCannotRestartReleasedPlayer() {
        session.activate("video", 0.5f);
        FakePlayer first = factory.players.get(0);
        session.deactivate();
        session.activate("video", 0.75f);
        first.events.prepared();
        assertEquals(0, first.starts);
        assertEquals(1, first.releases);
        FakePlayer second = factory.players.get(1);
        second.events.prepared();
        assertEquals(1, second.starts);
        assertEquals(0.75f, second.speed, 0f);
        assertEquals(1, videos);
    }
    @Test public void failuresDoNotRetryUntilNewActivation() {
        session.activate("bad", 0.5f);
        FakePlayer first = factory.players.get(0);
        first.events.failed();
        first.events.prepared();
        session.activate("bad", 0.5f);
        assertEquals(1, factory.players.size());
        assertEquals(1, failures);
        assertEquals(0, videos);
        session.deactivate();
        session.activate("bad", 0.5f);
        assertEquals(2, factory.players.size());
    }
    @Test public void timeoutAndLateCallbacksReleaseExactlyOnce() {
        session.activate("slow", 0.5f);
        FakePlayer player = factory.players.get(0);
        timer.fire();
        player.events.failed();
        player.events.prepared();
        session.deactivate();
        assertEquals(1, player.releases);
        assertEquals(1, failures);
        assertEquals(0, player.starts);
    }
    @Test public void repeatedPreparedAndActivateDoNotStartTwice() {
        session.activate("video", 2f);
        FakePlayer player = factory.players.get(0);
        player.events.prepared();
        player.events.prepared();
        session.activate("video", 2f);
        assertEquals(1, player.starts);
        assertEquals(1, factory.players.size());
        assertNull(timer.task);
    }
    @Test public void throwingPreparationAndStartFallBack() {
        factory.throwPrepare = true;
        session.activate("video", 0.5f);
        assertEquals(1, failures);
        assertEquals(1, factory.players.get(0).releases);
        factory.throwPrepare = false;
        session.deactivate();
        session.activate("video", 0.5f);
        FakePlayer player = factory.players.get(1);
        player.throwStart = true;
        player.events.prepared();
        assertEquals(2, failures);
        assertEquals(1, player.releases);
    }
    @Test public void emptySourceAndClosedSessionDoNotAllocate() {
        session.activate("", 0.5f);
        session.deactivate();
        session.close();
        session.close();
        session.activate("video", 0.5f);
        assertTrue(factory.players.isEmpty());
    }
}
