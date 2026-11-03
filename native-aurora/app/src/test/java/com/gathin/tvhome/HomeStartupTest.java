package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeStartupTest {
    @Test public void disabledNeverOpensOnBootOrWake() {
        HomeStartup startup = new HomeStartup(false, false, 1);
        assertFalse(startup.boot(2));
        startup.screen(true);
        assertFalse(startup.takeLaunch(true, true));
    }

    @Test public void newBootOpensOnceAndRecordsTheBoot() {
        HomeStartup startup = new HomeStartup(true, true, 1);
        assertTrue(startup.boot(2));
        assertEquals(2, startup.handledBoot());
        assertTrue(startup.takeLaunch(true, true));
        assertFalse(startup.takeLaunch(true, true));
        assertFalse(startup.boot(2));
    }

    @Test public void reconnectingInTheSameBootDoesNotInterruptAnApp() {
        HomeStartup startup = new HomeStartup(true, true, 2);
        assertFalse(startup.boot(2));
        assertFalse(startup.takeLaunch(true, true));
    }

    @Test public void eachRealWakeOpensOnceButDuplicateScreenOnDoesNotRearm() {
        HomeStartup startup = new HomeStartup(true, false, 2);
        startup.screen(true);
        assertTrue(startup.takeLaunch(true, true));
        startup.screen(true);
        assertFalse(startup.takeLaunch(true, true));
        startup.screen(false);
        startup.screen(true);
        assertTrue(startup.takeLaunch(true, true));
    }

    @Test public void remoteInteractionCancelsPendingStartupWithoutRearming() {
        HomeStartup startup = new HomeStartup(true, false, 2);
        startup.screen(true);
        startup.cancel();
        assertFalse(startup.takeLaunch(true, true));
        startup.screen(true);
        assertFalse(startup.takeLaunch(true, true));
    }

    @Test public void screenOffCancelsTheDelayedLaunch() {
        HomeStartup startup = new HomeStartup(true, true, 1);
        startup.boot(2);
        startup.screen(false);
        assertFalse(startup.takeLaunch(false, true));
        startup.screen(true);
        assertTrue(startup.takeLaunch(true, true));
    }

    @Test public void lockedStartupWaitsForUnlockWithoutConsumingTheRequest() {
        HomeStartup startup = new HomeStartup(true, true, 1);
        startup.boot(2);
        assertFalse(startup.takeLaunch(true, false));
        assertTrue(startup.pending());
        assertTrue(startup.takeLaunch(true, true));
    }

    @Test public void noLaunchOrScreenWakeWhenTheDisplayIsNotInteractive() {
        HomeStartup startup = new HomeStartup(true, true, 1);
        startup.boot(2);
        assertFalse(startup.takeLaunch(false, true));
        assertFalse(startup.pending());
    }

    @Test public void bootWhileAsleepWaitsForTheNextScreenOn() {
        HomeStartup startup = new HomeStartup(true, false, 1);
        assertTrue(startup.boot(2));
        assertFalse(startup.pending());
        startup.screen(true);
        assertTrue(startup.takeLaunch(true, true));
    }

    @Test public void unknownBootDoesNotPretendToBeANewRestart() {
        HomeStartup startup = new HomeStartup(true, true, 1);
        assertFalse(startup.boot(-1));
        assertEquals(1, startup.handledBoot());
        assertFalse(startup.pending());
    }
}
