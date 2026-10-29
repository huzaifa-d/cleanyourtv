package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class SoundSessionTest {
    private static final class Backend implements SoundSession.Backend {
        int logins, moves, stops;
        @Override public void login() { logins++; }
        @Override public void navigation() { moves++; }
        @Override public void stop() { stops++; }
    }

    @Test public void loginWaitsForBothLoadingAndForeground() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, false);
        session.loaded(true);
        assertEquals(0, backend.logins);
        session.setActive(true);
        assertEquals(1, backend.logins);
        assertTrue(session.loginPlayed());
    }

    @Test public void lateLoadStartsLoginOnlyOnce() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, false);
        session.setActive(true);
        session.loaded(true);
        session.loaded(true);
        session.setActive(true);
        assertEquals(1, backend.logins);
    }

    @Test public void returnFromAnotherAppDoesNotReplayLogin() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, false);
        session.loaded(true);
        session.setActive(true);
        session.setActive(false);
        session.setActive(true);
        assertEquals(1, backend.logins);
        assertEquals(1, backend.stops);
    }

    @Test public void restoredActivityDoesNotReplayLogin() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, true);
        session.loaded(true);
        session.setActive(true);
        assertEquals(0, backend.logins);
    }

    @Test public void navigationNeedsLoadedSoundAndAnActualFocusChange() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, false);
        Object first = new Object(), second = new Object();
        session.setActive(true);
        session.focusChanged(first, second);
        assertEquals(0, backend.moves);
        session.loaded(false);
        session.focusChanged(null, first);
        session.focusChanged(first, first);
        session.focusChanged(first, null);
        assertEquals(0, backend.moves);
        session.focusChanged(first, second);
        assertEquals(1, backend.moves);
    }

    @Test public void navigationStopsWhenInactiveOrFailed() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, false);
        Object first = new Object(), second = new Object();
        session.loaded(false);
        session.focusChanged(first, second);
        session.setActive(true);
        session.navigationFailed();
        session.focusChanged(first, second);
        assertFalse(session.hasNavigation());
        assertEquals(0, backend.moves);
    }

    @Test public void closeStopsAndIgnoresLateCallbacks() {
        Backend backend = new Backend();
        SoundSession session = new SoundSession(backend, false);
        session.setActive(true);
        session.close();
        session.close();
        session.loaded(true);
        session.loaded(false);
        session.setActive(true);
        session.focusChanged(new Object(), new Object());
        assertEquals(1, backend.stops);
        assertEquals(0, backend.logins);
        assertEquals(0, backend.moves);
        assertFalse(session.hasNavigation());
    }
}
