package com.gathin.tvhome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Schedule uses wall time; transitions advance only by explicitly supplied visible time. */
final class ThemeScene {
    static final long FADE_MILLIS = 2000, HOLD_MILLIS = 3000, MORPH_MILLIS = 2000;
    boolean night;
    double nightBlend;
    long introMillis;
    MoonPhase phase = MoonPhase.at(0);
    private boolean initialized;
    private long nightId, fadeMillis = FADE_MILLIS;
    private double fadeFrom;

    void update(long epochMillis, ZoneId zone, boolean animate) {
        ZonedDateTime local = Instant.ofEpochMilli(epochMillis).atZone(zone);
        boolean nextNight = local.getHour() < 6 || local.getHour() >= 18;
        long nextId = local.toLocalDate().minusDays(local.getHour() < 6 ? 1 : 0).toEpochDay();
        phase = MoonPhase.at(epochMillis);
        if (nextNight && (!initialized || !night || nextId != nightId)) introMillis = 0;
        if (!initialized) {
            nightBlend = nextNight ? 1 : 0;
            initialized = true;
        } else if (night != nextNight) {
            fadeFrom = nightBlend;
            fadeMillis = 0;
        }
        night = nextNight;
        nightId = nextId;
        advance(0, animate);
    }

    void advance(long visibleMillis, boolean animate) {
        long remaining = Math.max(0, visibleMillis);
        if (!animate) {
            fadeMillis = FADE_MILLIS;
            nightBlend = night ? 1 : 0;
        } else if (fadeMillis < FADE_MILLIS) {
            long consumed = Math.min(remaining, FADE_MILLIS - fadeMillis);
            fadeMillis += consumed;
            remaining -= consumed;
            nightBlend = fadeFrom + ((night ? 1 : 0) - fadeFrom)
                    * smooth(fadeMillis / (double) FADE_MILLIS);
        }
        if (night && fadeMillis == FADE_MILLIS) {
            introMillis = Math.min(HOLD_MILLIS + MORPH_MILLIS, introMillis + remaining);
            if (!animate && introMillis >= HOLD_MILLIS) introMillis = HOLD_MILLIS + MORPH_MILLIS;
        }
    }

    double illumination() {
        double progress = smooth(Math.max(0, Math.min(1,
                (introMillis - HOLD_MILLIS) / (double) MORPH_MILLIS)));
        double incidence = Math.acos(2 * phase.fraction - 1);
        return (1 + Math.cos(incidence * progress)) / 2;
    }

    boolean pending() {
        return fadeMillis < FADE_MILLIS || (night && introMillis < HOLD_MILLIS + MORPH_MILLIS);
    }

    long nextDelay() {
        return fadeMillis == FADE_MILLIS && night && introMillis < HOLD_MILLIS
                ? HOLD_MILLIS - introMillis : 0;
    }

    double[] save() {
        return new double[] {initialized ? 1 : 0, night ? 1 : 0, nightId,
                nightBlend, fadeFrom, fadeMillis, introMillis};
    }

    void restore(double[] saved) {
        if (saved == null || saved.length != 7) return;
        for (double value : saved) if (!Double.isFinite(value)) return;
        initialized = saved[0] == 1;
        night = saved[1] == 1;
        nightId = (long) saved[2];
        nightBlend = Math.max(0, Math.min(1, saved[3]));
        fadeFrom = Math.max(0, Math.min(1, saved[4]));
        fadeMillis = Math.max(0, Math.min(FADE_MILLIS, (long) saved[5]));
        introMillis = Math.max(0, Math.min(HOLD_MILLIS + MORPH_MILLIS, (long) saved[6]));
    }

    private static double smooth(double value) {
        return value * value * value * (value * (value * 6 - 15) + 10);
    }
}
