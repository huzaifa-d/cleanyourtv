package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class PetalMotionTest {
    private PetalMotion motion(double period, double phase, float opacity) {
        return new PetalMotion(0, .015f, period, phase, .4f, .08f, opacity, -30);
    }

    @Test public void wrapIsInvisibleAndCycleRepeats() {
        PetalMotion petal = motion(20000, 0, .8f);
        petal.sample(0);
        assertEquals(0, petal.alpha, 0);
        petal.sample(19999);
        assertTrue(petal.alpha < .001f);
        petal.sample(20000);
        assertEquals(0, petal.alpha, 0);
        petal.sample(7500);
        float x = petal.x, y = petal.y, alpha = petal.alpha, angle = petal.angle;
        petal.sample(27500);
        assertEquals(x, petal.x, .00001f);
        assertEquals(y, petal.y, .00001f);
        assertEquals(alpha, petal.alpha, .00001f);
        assertEquals(angle, petal.angle, .00001f);
    }

    @Test public void independentPeriodsAndOpacityDoNotMoveAsOneSheet() {
        PetalMotion fast = motion(14000, .2, .82f);
        PetalMotion slow = motion(36000, .2, .24f);
        fast.sample(1000);
        slow.sample(1000);
        assertTrue(fast.y > slow.y);
        assertNotEquals(fast.angle, slow.angle, .001f);
        assertEquals(.82f, fast.alpha, .001f);
        assertEquals(.24f, slow.alpha, .001f);
        PetalMotion phased = motion(14000, .7, .82f);
        phased.sample(1000);
        assertNotEquals(fast.y, phased.y, .001f);
    }
}
