package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

public class ThemeSceneTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private long time(String utc) { return Instant.parse(utc).toEpochMilli(); }
    private ThemeScene start(String utc) {
        ThemeScene scene = new ThemeScene();
        scene.update(time(utc), UTC, true);
        return scene;
    }

    @Test public void localScheduleIncludesSixButExcludesEighteen() {
        assertTrue(start("2026-03-22T05:59:59Z").night);
        assertFalse(start("2026-03-22T06:00:00Z").night);
        assertFalse(start("2026-03-22T17:59:59Z").night);
        assertTrue(start("2026-03-22T18:00:00Z").night);
        ThemeScene scene = new ThemeScene();
        scene.update(time("2026-03-22T12:00:00Z"), ZoneId.of("Australia/Sydney"), true);
        assertTrue(scene.night);
        scene.update(time("2026-03-22T12:00:00Z"), UTC, true);
        assertFalse(scene.night);
    }

    @Test public void coldNightHoldsForThreeVisibleSecondsThenMorphsForTwo() {
        ThemeScene scene = start("2026-03-22T20:00:00Z");
        assertEquals(1, scene.nightBlend, 0);
        scene.advance(2999, true);
        assertEquals(1, scene.illumination(), 0);
        scene.advance(1001, true);
        assertTrue(scene.illumination() < 1 && scene.illumination() > scene.phase.fraction);
        scene.advance(1000, true);
        assertEquals(scene.phase.fraction, scene.illumination(), 1e-9);
        assertFalse(scene.pending());
    }

    @Test public void duskFinishesCrossfadeBeforeStartingHoldAndDawnRemovesMoon() {
        ThemeScene scene = start("2026-03-22T17:59:00Z");
        scene.update(time("2026-03-22T18:00:00Z"), UTC, true);
        scene.advance(1000, true);
        assertEquals(.5, scene.nightBlend, 1e-6);
        assertEquals(0, scene.introMillis);
        scene.advance(1000 + 2999, true);
        assertEquals(1, scene.nightBlend, 0);
        assertEquals(1, scene.illumination(), 0);
        scene.advance(2001, true);
        assertFalse(scene.pending());
        scene.update(time("2026-03-23T06:00:00Z"), UTC, true);
        scene.advance(1000, true);
        assertEquals(.5, scene.nightBlend, 1e-6);
        scene.advance(1000, true);
        assertEquals(0, scene.nightBlend, 0);
        assertFalse(scene.pending());
    }

    @Test public void midnightAndRestorationDoNotReplayButNextNightDoes() {
        ThemeScene scene = start("2026-03-22T23:59:00Z");
        scene.advance(5000, true);
        scene.update(time("2026-03-23T00:00:00Z"), UTC, true);
        assertEquals(5000, scene.introMillis);
        ThemeScene restored = new ThemeScene();
        restored.restore(scene.save());
        restored.update(time("2026-03-23T01:00:00Z"), UTC, true);
        assertEquals(5000, restored.introMillis);
        restored.update(time("2026-03-23T20:00:00Z"), UTC, true);
        assertEquals(0, restored.introMillis);
        assertEquals(1, restored.nightBlend, 0);
    }

    @Test public void pauseAndRestorePreserveRemainingHoldAndMorph() {
        ThemeScene scene = start("2026-03-22T20:00:00Z");
        scene.advance(1700, true);
        ThemeScene restored = new ThemeScene();
        restored.restore(scene.save());
        restored.update(time("2026-03-22T22:00:00Z"), UTC, true);
        assertEquals(1700, restored.introMillis);
        restored.advance(1300, true);
        assertEquals(1, restored.illumination(), 0);
        restored.advance(600, true);
        double value = restored.illumination();
        ThemeScene again = new ThemeScene();
        again.restore(restored.save());
        again.update(time("2026-03-22T22:00:00Z"), UTC, true);
        assertEquals(value, again.illumination(), 1e-9);
        again.advance(1400, true);
        assertEquals(again.phase.fraction, again.illumination(), 1e-9);
    }

    @Test public void reducedMotionSkipsTransitionsButStillHoldsForThreeSeconds() {
        ThemeScene scene = start("2026-03-22T12:00:00Z");
        scene.update(time("2026-03-22T18:00:00Z"), UTC, false);
        assertEquals(1, scene.nightBlend, 0);
        scene.advance(2999, false);
        assertEquals(1, scene.illumination(), 0);
        scene.advance(1, false);
        assertEquals(scene.phase.fraction, scene.illumination(), 1e-9);
        assertFalse(scene.pending());
    }

    @Test public void interruptedCrossfadeContinuesFromCurrentImageInsteadOfSnapping() {
        ThemeScene scene = start("2026-03-22T12:00:00Z");
        scene.update(time("2026-03-22T18:00:00Z"), UTC, true);
        scene.advance(800, true);
        double previous = scene.nightBlend;
        scene.update(time("2026-03-22T12:00:00Z"), UTC, true);
        assertEquals(previous, scene.nightBlend, 0);
        scene.advance(1000, true);
        assertEquals(previous / 2, scene.nightBlend, 1e-9);
        scene.advance(1000, true);
        assertEquals(0, scene.nightBlend, 0);
    }
}
