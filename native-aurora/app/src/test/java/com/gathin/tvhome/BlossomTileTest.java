package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class BlossomTileTest {
    @Test public void approvedCurveGrowsFor280msThenSettlesBy700msWithSmoothJoins() {
        assertEquals(1f, BlossomTile.focusScale(0f, 1f), .00001f);
        assertEquals(1.085f, BlossomTile.focusScale(.2f, 1f), .00001f);
        assertEquals(1.17f, BlossomTile.focusScale(.4f, 1f), .00001f);
        assertEquals(1.155f, BlossomTile.focusScale(.7f, 1f), .00001f);
        assertEquals(1.14f, BlossomTile.focusScale(1f, 1f), .00001f);
        assertEquals(1.14f, BlossomTile.focusScale(2f, 1f), .00001f);
        for (float join : new float[] {0f, .4f, 1f}) {
            float left = BlossomTile.focusScale(join - .001f, 1f);
            float right = BlossomTile.focusScale(join + .001f, 1f);
            float centre = BlossomTile.focusScale(join, 1f);
            assertEquals("Left-hand velocity must approach zero", 0f, (centre - left) / .001f, .001f);
            assertEquals("Right-hand velocity must approach zero", 0f, (right - centre) / .001f, .001f);
        }
        float previous = 1f;
        for (int step = 0; step <= 100; step++) {
            float scale = BlossomTile.focusScale(step / 100f, 1f);
            assertTrue(scale >= 1f && scale <= 1.17f);
            assertTrue(step <= 40 ? scale >= previous - .00001f : scale <= previous + .00001f);
            previous = scale;
        }
    }

    @Test public void interruptedFocusStartsAtItsCurrentSizeWithoutSnapping() {
        for (float current : new float[] {1.03f, 1.14f, 1.165f}) {
            assertEquals(current, BlossomTile.focusScale(0f, current), .00001f);
            assertEquals(1.17f, BlossomTile.focusScale(.4f, current), .00001f);
            assertEquals(1.14f, BlossomTile.focusScale(1f, current), .00001f);
        }
    }

    @Test public void innerPulseFadesInAndOutWithoutResidualOrRepeatedGlow() {
        assertEquals(0f, BlossomTile.focusPulse(0f), 0f);
        assertEquals(.06f, BlossomTile.focusPulse(.25f), .00001f);
        assertEquals(.12f, BlossomTile.focusPulse(.5f), .00001f);
        assertEquals(.06f, BlossomTile.focusPulse(.75f), .00001f);
        assertEquals(0f, BlossomTile.focusPulse(1f), 0f);
        assertEquals(0f, BlossomTile.focusPulse(2f), 0f);
    }
}
