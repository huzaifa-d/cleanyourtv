package com.gathin.tvhome;

import static org.junit.Assert.*;
import static com.gathin.tvhome.DailyStandby.Cutoff.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public class DailyStandbyTest {
    private ZonedDateTime time(String value) { return ZonedDateTime.parse(value); }
    private long millis(String value) { return time(value).toInstant().toEpochMilli(); }

    @Test public void bothCutoffsUseTheTvTimezone() {
        ZonedDateTime now = time("2026-10-29T19:00:00-07:00[America/Los_Angeles]");
        assertEquals(time("2026-10-29T20:03:00-07:00[America/Los_Angeles]"),
                DailyStandby.next(now, "", WEEKDAYS));
        assertEquals(time("2026-10-29T20:30:00-07:00[America/Los_Angeles]"),
                DailyStandby.next(now, "", EVERY_DAY));
    }

    @Test public void startingAtEightOhThreeDoesNotCatchUpTheFirstAlarm() {
        ZonedDateTime cutoff = time("2026-10-29T20:03:00Z");
        assertEquals(cutoff.plusDays(1), DailyStandby.next(cutoff, "", WEEKDAYS));
        assertEquals(time("2026-10-29T20:30:00Z"), DailyStandby.next(cutoff, "", EVERY_DAY));
    }

    @Test public void turningOnAfterEightOhThreeStillSchedulesEightThirty() {
        ZonedDateTime now = time("2026-10-30T20:10:00Z");
        assertEquals(time("2026-11-02T20:03:00Z"), DailyStandby.next(now, "", WEEKDAYS));
        assertEquals(time("2026-10-30T20:30:00Z"), DailyStandby.next(now, "", EVERY_DAY));
    }

    @Test public void startingAtOrAfterEightThirtyWaitsForFutureEligibleDates() {
        for (String instant : new String[] {"2026-10-30T20:30:00Z", "2026-10-30T22:00:00Z"}) {
            ZonedDateTime now = time(instant);
            assertEquals(time("2026-11-02T20:03:00Z"), DailyStandby.next(now, "", WEEKDAYS));
            assertEquals(time("2026-10-31T20:30:00Z"), DailyStandby.next(now, "", EVERY_DAY));
        }
    }

    @Test public void weekendsOnlyHaveTheEightThirtyCutoff() {
        for (String day : new String[] {"2026-10-31", "2026-11-01"}) {
            ZonedDateTime now = time(day + "T19:00:00Z");
            assertEquals(time("2026-11-02T20:03:00Z"), DailyStandby.next(now, "", WEEKDAYS));
            assertEquals(time(day + "T20:30:00Z"), DailyStandby.next(now, "", EVERY_DAY));
            assertFalse(DailyStandby.isDue(time(day + "T20:03:00Z"),
                    millis(day + "T20:03:00Z"), "", "", WEEKDAYS));
        }
    }

    @Test public void skipTodayDisablesBothAndExpiresOnTheirNextEligibleDays() {
        ZonedDateTime friday = time("2026-10-30T19:00:00Z");
        assertEquals(time("2026-11-02T20:03:00Z"),
                DailyStandby.next(friday, "2026-10-30", WEEKDAYS));
        assertEquals(time("2026-10-31T20:30:00Z"),
                DailyStandby.next(friday, "2026-10-30", EVERY_DAY));
        assertEquals(time("2026-10-31T20:30:00Z"),
                DailyStandby.next(friday.plusDays(1), "2026-10-30", EVERY_DAY));
        assertEquals(time("2026-11-02T20:03:00Z"),
                DailyStandby.next(friday.plusDays(3), "2026-10-30", WEEKDAYS));
    }

    @Test public void undoingSkipRestoresBothFutureCutoffs() {
        ZonedDateTime now = time("2026-10-29T19:00:00Z");
        assertEquals(time("2026-10-29T20:03:00Z"), DailyStandby.next(now, "", WEEKDAYS));
        assertEquals(time("2026-10-29T20:30:00Z"), DailyStandby.next(now, "", EVERY_DAY));
    }

    @Test public void aClockChangeDoesNotLoseTheSkippedCalendarDate() {
        ZonedDateTime now = time("2026-10-29T21:00:00Z");
        assertEquals(time("2026-11-02T20:03:00Z"),
                DailyStandby.next(now, "2026-10-30", WEEKDAYS));
        assertEquals(time("2026-10-31T20:30:00Z"),
                DailyStandby.next(now, "2026-10-30", EVERY_DAY));
    }

    @Test public void usesCalendarDaysAcrossDaylightSavingChanges() {
        ZonedDateTime fall = time("2026-10-31T21:00:00-07:00[America/Los_Angeles]");
        assertEquals(time("2026-11-02T20:03:00-08:00[America/Los_Angeles]"),
                DailyStandby.next(fall, "", WEEKDAYS));
        assertEquals(time("2026-11-01T20:30:00-08:00[America/Los_Angeles]"),
                DailyStandby.next(fall, "", EVERY_DAY));
        ZonedDateTime spring = time("2026-03-07T21:00:00-08:00[America/Los_Angeles]");
        assertEquals(time("2026-03-09T20:03:00-07:00[America/Los_Angeles]"),
                DailyStandby.next(spring, "", WEEKDAYS));
        assertEquals(time("2026-03-08T20:30:00-07:00[America/Los_Angeles]"),
                DailyStandby.next(spring, "", EVERY_DAY));
    }

    @Test public void anAlarmRunsDuringTheCutoffMinuteOnly() {
        for (DailyStandby.Cutoff cutoff : DailyStandby.Cutoff.values()) {
            ZonedDateTime at = DailyStandby.next(time("2026-10-29T19:00:00Z"), "", cutoff);
            long due = at.toInstant().toEpochMilli();
            assertTrue(DailyStandby.isDue(at, due, "", "", cutoff));
            assertTrue(DailyStandby.isDue(at.plusNanos(59_999_000_000L), due, "", "", cutoff));
            assertFalse(DailyStandby.isDue(at.minusSeconds(1), due, "", "", cutoff));
            assertFalse(DailyStandby.isDue(at.plusMinutes(1), due, "", "", cutoff));
        }
    }

    @Test public void missedAlarmsDoNotShutDownOnALaterWakeOrReboot() {
        long due = millis("2026-10-29T20:03:00Z");
        assertFalse(DailyStandby.isDue(time("2026-10-29T22:00:00Z"), due, "", "", WEEKDAYS));
        assertFalse(DailyStandby.isDue(time("2026-10-30T20:03:00Z"), due, "", "", WEEKDAYS));
        assertFalse(DailyStandby.isDue(time("2026-10-29T20:03:00Z"), 0, "", "", WEEKDAYS));
        assertFalse(DailyStandby.isDue(time("2026-10-30T20:30:00Z"),
                millis("2026-10-29T20:30:00Z"), "", "", EVERY_DAY));
    }

    @Test public void skipAndTheCutoffsOwnHandledDatePreventStandby() {
        for (DailyStandby.Cutoff cutoff : DailyStandby.Cutoff.values()) {
            ZonedDateTime at = DailyStandby.next(time("2026-10-29T19:00:00Z"), "", cutoff);
            long due = at.toInstant().toEpochMilli();
            assertFalse(DailyStandby.isDue(at, due, "2026-10-29", "", cutoff));
            assertFalse(DailyStandby.isDue(at, due, "", "2026-10-29", cutoff));
            assertTrue(DailyStandby.isDue(at, due, "2026-10-28", "2026-10-28", cutoff));
        }
    }

    @Test public void firstAlarmCompletionMustNotSuppressTheSecond() {
        assertFalse(DailyStandby.isDue(time("2026-10-29T20:03:10Z"),
                millis("2026-10-29T20:03:00Z"), "", "2026-10-29", WEEKDAYS));
        assertTrue(DailyStandby.isDue(time("2026-10-29T20:30:10Z"),
                millis("2026-10-29T20:30:00Z"), "", "", EVERY_DAY));
        assertNotEquals(WEEKDAYS.id, EVERY_DAY.id);
        assertEquals(WEEKDAYS, DailyStandby.cutoffForId(2003));
        assertEquals(EVERY_DAY, DailyStandby.cutoffForId(2030));
        assertNull(DailyStandby.cutoffForId(999));
    }

    @Test public void independentStateKeepsTheOldFirstAlarmKeysForUpgrade() {
        assertEquals("next_alarm", StandbyScheduler.key("next_alarm", WEEKDAYS));
        assertEquals("handled_date", StandbyScheduler.key("handled_date", WEEKDAYS));
        assertEquals("next_alarm_2030", StandbyScheduler.key("next_alarm", EVERY_DAY));
        assertEquals("handled_date_2030", StandbyScheduler.key("handled_date", EVERY_DAY));
    }

    @Test public void aChangedTimezoneInvalidatesTheOldCutoff() {
        ZonedDateTime original = time("2026-10-29T20:03:00-07:00[America/Los_Angeles]");
        ZonedDateTime changed = original.withZoneSameInstant(ZoneId.of("America/New_York"));
        assertFalse(DailyStandby.isDue(changed, original.toInstant().toEpochMilli(), "", "", WEEKDAYS));
        assertEquals(time("2026-10-30T20:03:00-04:00[America/New_York]"),
                DailyStandby.next(changed, "", WEEKDAYS));
    }

    @Test public void anArbitraryOrEarlyTriggerCannotTurnOffTheTv() {
        ZonedDateTime now = time("2026-10-29T19:00:00Z");
        assertFalse(DailyStandby.isDue(now, now.toInstant().toEpochMilli(), "", "", WEEKDAYS));
        assertFalse(DailyStandby.isDue(time("2026-10-29T20:03:10Z"),
                millis("2026-10-29T20:03:05Z"), "", "", WEEKDAYS));
        assertFalse(DailyStandby.isDue(time("2026-10-29T20:30:00Z"),
                millis("2026-10-29T20:30:00Z"), "", "", WEEKDAYS));
    }
}
