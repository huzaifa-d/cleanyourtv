package com.gathin.tvhome;

final class PetalMotion {
    final int texture;
    final float width;
    private final double period, phase;
    private final float startX, drift, opacity, startAngle;
    float x, y, alpha, angle;

    PetalMotion(int texture, float width, double period, double phase,
            float startX, float drift, float opacity, float startAngle) {
        this.texture = texture;
        this.width = width;
        this.period = period;
        this.phase = phase;
        this.startX = startX;
        this.drift = drift;
        this.opacity = opacity;
        this.startAngle = startAngle;
    }

    void sample(long elapsedMillis) {
        double progress = (elapsedMillis / period + phase) % 1;
        x = (float) (startX + drift * progress + .015 * Math.sin(progress * 3 * Math.PI));
        y = (float) (-.06 + progress * 1.12);
        alpha = (float) (opacity * Math.min(1, Math.min(progress / .08, (1 - progress) / .12)));
        angle = (float) (startAngle + progress * 340);
    }
}
