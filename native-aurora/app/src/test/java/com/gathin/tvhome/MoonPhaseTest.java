package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.time.Instant;
import org.junit.Test;

public class MoonPhaseTest {
    private MoonPhase phase(String utc) {
        return MoonPhase.at(Instant.parse(utc).toEpochMilli());
    }

    @Test public void agreesWithIndependentUsnoPrincipalPhasesAcrossTheYear() {
        // USNO: https://aa.usno.navy.mil/api/moon/phases/year?year=2026 (UTC).
        String[] times = {"2026-01-03T10:03:00Z", "2026-01-10T15:48:00Z",
                "2026-01-18T19:52:00Z", "2026-01-26T04:47:00Z",
                "2026-03-03T11:38:00Z", "2026-03-11T09:38:00Z",
                "2026-03-19T01:23:00Z", "2026-03-25T19:18:00Z",
                "2026-06-29T23:56:00Z", "2026-07-07T19:29:00Z",
                "2026-07-14T09:43:00Z", "2026-07-21T11:05:00Z",
                "2026-12-24T01:28:00Z", "2026-12-30T18:59:00Z",
                "2026-12-09T00:52:00Z", "2026-12-17T05:42:00Z"};
        double[] fractions = {1, .5, 0, .5};
        for (int i = 0; i < times.length; i++) {
            MoonPhase actual = phase(times[i]);
            assertEquals(times[i], fractions[i % 4], actual.fraction, .035);
            assertTrue(actual.fraction >= 0 && actual.fraction <= 1);
            if (i % 4 == 1) assertFalse(times[i], actual.waxing);
            if (i % 4 == 3) assertTrue(times[i], actual.waxing);
        }
    }

    @Test public void realDatesChangeTheShapeRatherThanOnlyUsingAnAverageMonth() {
        MoonPhase waxing = phase("2026-03-22T12:00:00Z");
        MoonPhase waning = phase("2026-03-15T12:00:00Z");
        assertTrue(waxing.waxing);
        assertFalse(waning.waxing);
        assertTrue(waxing.fraction > .05 && waxing.fraction < .30);
        assertTrue(waning.fraction > .05 && waning.fraction < .30);
        assertNotEquals(waxing.fraction, phase("2026-04-22T12:00:00Z").fraction, .1);
    }
}
