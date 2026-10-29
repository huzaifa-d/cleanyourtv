package com.gathin.tvhome;

import static android.view.KeyEvent.*;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class HomeKeyHandlerTest {
    @Test public void homeLaunchesOnceAndConsumesBothHalvesOfPress() {
        AtomicInteger launches = new AtomicInteger();
        HomeKeyHandler keys = new HomeKeyHandler(() -> { launches.incrementAndGet(); return true; });
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_DOWN, 0));
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
        assertEquals(1, launches.get());
    }

    @Test public void holdingHomeDoesNotRepeatedlyLaunch() {
        AtomicInteger launches = new AtomicInteger();
        HomeKeyHandler keys = new HomeKeyHandler(() -> { launches.incrementAndGet(); return true; });
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_DOWN, 0));
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_DOWN, 1));
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_DOWN, 8));
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
        assertEquals(1, launches.get());
    }

    @Test public void unrelatedKeysPassThrough() {
        HomeKeyHandler keys = new HomeKeyHandler(() -> { fail("Unexpected launch"); return true; });
        for (int code : new int[] {KEYCODE_BACK, KEYCODE_DPAD_CENTER, KEYCODE_DPAD_UP,
                KEYCODE_VOLUME_UP, KEYCODE_POWER, KEYCODE_ALL_APPS}) {
            assertFalse(keys.handle(code, ACTION_DOWN, 0));
            assertFalse(keys.handle(code, ACTION_UP, 0));
        }
    }

    @Test public void failedLaunchLeavesWholeHomePressForSystem() {
        HomeKeyHandler keys = new HomeKeyHandler(() -> false);
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_DOWN, 0));
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_DOWN, 1));
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
    }

    @Test public void unpairedUpAndRepeatsPassThrough() {
        HomeKeyHandler keys = new HomeKeyHandler(() -> { fail("Unexpected launch"); return true; });
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_DOWN, 2));
    }

    @Test public void interruptClearsPendingConsumptionAndNextPressWorks() {
        AtomicInteger launches = new AtomicInteger();
        HomeKeyHandler keys = new HomeKeyHandler(() -> { launches.incrementAndGet(); return true; });
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_DOWN, 0));
        keys.reset();
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_DOWN, 0));
        assertTrue(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
        assertFalse(keys.handle(KEYCODE_HOME, ACTION_UP, 0));
        assertEquals(2, launches.get());
    }
}
