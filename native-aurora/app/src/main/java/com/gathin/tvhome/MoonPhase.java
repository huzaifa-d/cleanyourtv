package com.gathin.tvhome;

/** Offline geocentric illumination, adapted from SunCalc 1.9.0 (BSD-2-Clause).
 * Copyright (c) 2014, Vladimir Agafonkin. See bundled SUNCALC-LICENSE.txt.
 */
final class MoonPhase {
    private static final double RAD = Math.PI / 180;
    private static final double OBLIQUITY = 23.4397 * RAD;
    final double fraction;
    final boolean waxing;

    private MoonPhase(double fraction, boolean waxing) {
        this.fraction = fraction;
        this.waxing = waxing;
    }

    static MoonPhase at(long epochMillis) {
        double days = epochMillis / 86400000.0 + 2440587.5 - 2451545;
        double solarAnomaly = RAD * (357.5291 + .98560028 * days);
        double solarLongitude = solarAnomaly + RAD * (1.9148 * Math.sin(solarAnomaly)
                + .02 * Math.sin(2 * solarAnomaly) + .0003 * Math.sin(3 * solarAnomaly))
                + RAD * 102.9372 + Math.PI;
        double lunarAnomaly = RAD * (134.963 + 13.064993 * days);
        double lunarLongitude = RAD * (218.316 + 13.176396 * days)
                + RAD * 6.289 * Math.sin(lunarAnomaly);
        double lunarLatitude = RAD * 5.128 * Math.sin(RAD * (93.272 + 13.229350 * days));
        double lunarDistance = 385001 - 20905 * Math.cos(lunarAnomaly);
        double solarRa = rightAscension(solarLongitude, 0);
        double solarDec = declination(solarLongitude, 0);
        double lunarRa = rightAscension(lunarLongitude, lunarLatitude);
        double lunarDec = declination(lunarLongitude, lunarLatitude);
        double separation = Math.acos(clamp(Math.sin(solarDec) * Math.sin(lunarDec)
                + Math.cos(solarDec) * Math.cos(lunarDec) * Math.cos(solarRa - lunarRa)));
        double incidence = Math.atan2(149598000 * Math.sin(separation),
                lunarDistance - 149598000 * Math.cos(separation));
        double angle = Math.atan2(Math.cos(solarDec) * Math.sin(solarRa - lunarRa),
                Math.sin(solarDec) * Math.cos(lunarDec)
                        - Math.cos(solarDec) * Math.sin(lunarDec) * Math.cos(solarRa - lunarRa));
        return new MoonPhase((1 + Math.cos(incidence)) / 2, angle < 0);
    }

    private static double rightAscension(double longitude, double latitude) {
        return Math.atan2(Math.sin(longitude) * Math.cos(OBLIQUITY)
                - Math.tan(latitude) * Math.sin(OBLIQUITY), Math.cos(longitude));
    }

    private static double declination(double longitude, double latitude) {
        return Math.asin(clamp(Math.sin(latitude) * Math.cos(OBLIQUITY)
                + Math.cos(latitude) * Math.sin(OBLIQUITY) * Math.sin(longitude)));
    }

    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
