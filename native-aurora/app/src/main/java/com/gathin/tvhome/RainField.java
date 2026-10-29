package com.gathin.tvhome;

import java.util.Random;

final class RainField {
    static final int COUNT = 300;
    final float[] length = new float[COUNT], alpha = new float[COUNT];
    private final float[] x = new float[COUNT], phase = new float[COUNT];
    private final int[] cycles = new int[COUNT];

    RainField() {
        Random random = new Random(28);
        for (int i = 0; i < COUNT; i++) {
            int depth = i < 160 ? 0 : i < 265 ? 1 : 2;
            x[i] = random.nextFloat() * 1100;
            phase[i] = random.nextFloat();
            length[i] = 5 + depth * 8 + random.nextFloat() * 9;
            alpha[i] = .12f + depth * .10f + random.nextFloat() * .10f;
            cycles[i] = 2 + depth * 2;
        }
    }

    void sample(int index, double seconds, float[] position) {
        double progress = seconds * cycles[index] / 12 + phase[index];
        progress -= Math.floor(progress);
        position[0] = x[index] - (float) progress * 90;
        position[1] = -length[index] * 2 + (float) progress * (540 + length[index] * 4);
    }
}
