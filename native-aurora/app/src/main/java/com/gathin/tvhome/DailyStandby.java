package com.gathin.tvhome;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZonedDateTime;

final class DailyStandby {
    enum Cutoff {
        WEEKDAYS(2003, 20, 3), EVERY_DAY(2030, 20, 30);

        final int id;
        final LocalTime time;

        Cutoff(int id, int hour, int minute) {
            this.id = id;
            time = LocalTime.of(hour, minute);
        }

        boolean applies(LocalDate date) {
            return this == EVERY_DAY || date.getDayOfWeek().getValue() <= 5;
        }
    }

    private DailyStandby() { }

    static Cutoff cutoffForId(int id) {
        for (Cutoff cutoff : Cutoff.values()) {
            if (cutoff.id == id) return cutoff;
        }
        return null;
    }

    static ZonedDateTime next(ZonedDateTime now, String skippedDate, Cutoff cutoff) {
        LocalDate date = now.toLocalDate();
        while (true) {
            ZonedDateTime next = date.atTime(cutoff.time).atZone(now.getZone());
            if (next.isAfter(now) && cutoff.applies(date) && !date.toString().equals(skippedDate)) {
                return next;
            }
            date = date.plusDays(1);
        }
    }

    static boolean isDue(ZonedDateTime now, long scheduledAt, String skippedDate, String handledDate,
            Cutoff cutoff) {
        String today = now.toLocalDate().toString();
        long due = now.toLocalDate().atTime(cutoff.time).atZone(now.getZone()).toInstant().toEpochMilli();
        long elapsed = now.toInstant().toEpochMilli() - due;
        return cutoff.applies(now.toLocalDate()) && scheduledAt == due && elapsed >= 0 && elapsed < 60_000
                && !today.equals(skippedDate) && !today.equals(handledDate);
    }
}
