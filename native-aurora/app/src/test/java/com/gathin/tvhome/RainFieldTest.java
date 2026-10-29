package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class RainFieldTest {
    @Test public void loopingAndBackwardsSeekingReproduceTheSameDropPositions() {
        RainField field = new RainField();
        float[] first = new float[2], other = new float[2];
        for (int i = 0; i < RainField.COUNT; i++) {
            field.sample(i, 0, first);
            field.sample(i, 12, other);
            assertArrayEquals(first, other, .0001f);
            field.sample(i, 8, other);
            field.sample(i, 0, other);
            assertArrayEquals(first, other, 0f);
        }
    }

    @Test public void rainTravelsDownwardRatherThanFlickeringInPlace() {
        RainField field = new RainField();
        float[] first = new float[2], later = new float[2];
        int falling = 0;
        for (int i = 0; i < RainField.COUNT; i++) {
            field.sample(i, 1, first);
            field.sample(i, 1.02, later);
            if (later[1] > first[1] && later[0] < first[0]) falling++;
        }
        assertTrue("Most drops must travel down-left between frames", falling > 280);
    }
}
