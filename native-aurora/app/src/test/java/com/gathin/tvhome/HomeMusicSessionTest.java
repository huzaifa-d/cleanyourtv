package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeMusicSessionTest {
    private static final class Player implements HomeMusicSession.Backend {
        int prepares, starts, releases;
        long generation;
        @Override public void prepare(long generation) { prepares++; this.generation = generation; }
        @Override public void start() { starts++; }
        @Override public void release() { releases++; }
    }

    @Test public void preparesOncePerVisibleHomeReturnAndWaitsForReadiness() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.setActive(true);
        assertEquals(1, player.prepares);
        assertEquals(0, player.starts);
        session.prepared(player.generation);
        session.prepared(player.generation);
        assertEquals(1, player.starts);
        session.setActive(false);
        assertEquals(1, player.releases);
        session.setActive(true);
        assertEquals(2, player.prepares);
        session.prepared(player.generation);
        assertEquals(2, player.starts);
    }

    @Test public void drawerOrOtherAppCancelsEvenAnUnpreparedTrack() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        long old = player.generation;
        session.setActive(false);
        session.prepared(old);
        assertEquals(0, player.starts);
        assertEquals(1, player.releases);
        session.setActive(true);
        session.prepared(old);
        assertEquals(0, player.starts);
        session.prepared(player.generation);
        assertEquals(1, player.starts);
    }

    @Test public void completionDoesNotLoopOrRestartUntilHomeIsReentered() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.prepared(player.generation);
        session.finished(player.generation);
        session.setActive(true);
        session.prepared(player.generation);
        assertEquals(1, player.starts);
        assertEquals(1, player.prepares);
        assertEquals(1, player.releases);
        session.setActive(false);
        session.setActive(true);
        assertEquals(2, player.prepares);
    }

    @Test public void focusLossOrFailureEndsTheVisitWithoutAnAutomaticRetry() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.finished(player.generation);
        session.setActive(true);
        assertEquals(1, player.prepares);
        assertEquals(0, player.starts);
        session.prepared(player.generation);
        assertEquals(0, player.starts);
    }

    @Test public void obsoleteCompletionCannotReleaseANewHomeVisit() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        long old = player.generation;
        session.setActive(false);
        session.setActive(true);
        session.finished(old);
        assertEquals(1, player.releases);
        session.prepared(player.generation);
        assertEquals(1, player.starts);
    }

    @Test public void closingReleasesOnceAndPreventsLateStarts() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        long pending = player.generation;
        session.close();
        session.close();
        session.setActive(true);
        session.prepared(pending);
        assertEquals(1, player.releases);
        assertEquals(1, player.prepares);
        assertEquals(0, player.starts);
    }
}
