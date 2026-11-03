package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeMusicSessionTest {
    private static final class Player implements HomeMusicSession.Backend {
        int prepares, starts, releases;
        int position, resumePosition;
        boolean positionAvailable = true;
        long generation;
        @Override public void prepare(long generation, int resumePosition) {
            prepares++;
            this.generation = generation;
            this.resumePosition = resumePosition;
        }
        @Override public Integer playingPosition() { return positionAvailable ? position : null; }
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
        player.position = 386000;
        session.completed(player.generation);
        session.setActive(true);
        session.prepared(player.generation);
        assertEquals(1, player.starts);
        assertEquals(1, player.prepares);
        assertEquals(1, player.releases);
        session.setActive(false);
        session.setActive(true);
        assertEquals(2, player.prepares);
        assertEquals(0, player.resumePosition);
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

    @Test public void returningHomeResumesFromPositionCapturedBeforeRelease() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.prepared(player.generation);
        player.position = 80000;
        session.setActive(false);
        player.position = 0;
        session.setActive(true);
        assertEquals(80000, player.resumePosition);
        session.prepared(player.generation);
        assertEquals(2, player.starts);
    }

    @Test public void focusLossPreservesThePositionForTheNextHomeVisit() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.prepared(player.generation);
        player.position = 42000;
        session.finished(player.generation);
        session.setActive(false);
        session.setActive(true);
        assertEquals(42000, player.resumePosition);
    }

    @Test public void leavingBeforePreparationOrSeekCompletesDoesNotLoseTheBookmark() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.prepared(player.generation);
        player.position = 80000;
        session.setActive(false);
        session.setActive(true);
        long cancelled = player.generation;
        player.position = 0;
        session.setActive(false);
        session.setActive(true);
        session.prepared(cancelled);
        assertEquals(80000, player.resumePosition);
        assertEquals(1, player.starts);
    }

    @Test public void restartingCherryDoesNotRetainTheBookmark() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.prepared(player.generation);
        player.position = 80000;
        session.close();
        HomeMusicSession restarted = new HomeMusicSession(player);
        restarted.setActive(true);
        assertEquals(0, player.resumePosition);
    }

    @Test public void obsoleteCompletionCannotClearANewerBookmark() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        long old = player.generation;
        session.prepared(old);
        player.position = 80000;
        session.setActive(false);
        session.setActive(true);
        session.completed(old);
        session.setActive(false);
        session.setActive(true);
        assertEquals(80000, player.resumePosition);
    }

    @Test public void unavailablePlaybackPositionPreservesThePreviousBookmark() {
        Player player = new Player();
        HomeMusicSession session = new HomeMusicSession(player);
        session.setActive(true);
        session.prepared(player.generation);
        player.position = 80000;
        session.setActive(false);
        session.setActive(true);
        session.prepared(player.generation);
        player.positionAvailable = false;
        session.finished(player.generation);
        session.setActive(false);
        session.setActive(true);
        assertEquals(80000, player.resumePosition);
    }
}
