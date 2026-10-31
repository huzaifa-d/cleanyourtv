package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import com.gathin.tvhome.DailyStandby.Cutoff;

final class StandbyScheduler {
    static final String ACTION_FIRE = "com.huzaifa.cherry.launcher.DAILY_STANDBY";
    static final String EXTRA_SCHEDULED_AT = "scheduled_at";
    static final String EXTRA_CUTOFF_ID = "cutoff_id";
    private static final String TAG = "CherryStandby";
    private static final String SKIP = "skipped_date", HANDLED = "handled_date", NEXT = "next_alarm";
    private static final String ERROR = "last_error";

    private StandbyScheduler() { }

    static SharedPreferences store(Context context) {
        return context.getSharedPreferences("daily_standby", Context.MODE_PRIVATE);
    }

    private static Intent alarmIntent(Context context, Cutoff cutoff) {
        return new Intent(context, StandbyReceiver.class).setAction(ACTION_FIRE)
                .putExtra(EXTRA_CUTOFF_ID, cutoff.id);
    }

    static String key(String base, Cutoff cutoff) {
        // Preserve the older 8:03 alarm's state when upgrading; 8:30 has independent state.
        return cutoff == Cutoff.WEEKDAYS ? base : base + "_" + cutoff.id;
    }

    static boolean canSchedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        return alarms != null && (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms());
    }

    static boolean skippedToday(Context context) {
        return ZonedDateTime.now().toLocalDate().toString().equals(store(context).getString(SKIP, ""));
    }

    static boolean skipToday(Context context, boolean skipped) {
        String date = skipped ? ZonedDateTime.now().toLocalDate().toString() : "";
        if (!store(context).edit().putString(SKIP, date).commit()) {
            failure(context, R.string.standby_save_failed);
            return false;
        }
        reschedule(context);
        return true;
    }

    static void reschedule(Context context) {
        for (Cutoff cutoff : Cutoff.values()) schedule(context, true, cutoff);
    }

    private static void schedule(Context context, boolean preserveDueAlarm, Cutoff cutoff) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        SharedPreferences preferences = store(context);
        if (Build.VERSION.SDK_INT < 28 || !canSchedule(context) || !HomeKeyService.isEnabled(context)) {
            PendingIntent existing = PendingIntent.getBroadcast(context, cutoff.id, alarmIntent(context, cutoff),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (existing != null && alarms != null) alarms.cancel(existing);
            preferences.edit().putLong(key(NEXT, cutoff), 0).apply();
            Log.w(TAG, "Daily standby needs Android 9+, exact-alarm access and the Home helper");
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        String skipped = preferences.getString(SKIP, "");
        long previous = preferences.getLong(key(NEXT, cutoff), 0);
        // Do not replace an alarm that is already being delivered during the cutoff minute.
        if (preserveDueAlarm && DailyStandby.isDue(now, previous, skipped,
                preferences.getString(key(HANDLED, cutoff), ""), cutoff)
                && PendingIntent.getBroadcast(context, cutoff.id, alarmIntent(context, cutoff),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null) return;
        long next = DailyStandby.next(now, skipped, cutoff).toInstant().toEpochMilli();
        PendingIntent alarm = PendingIntent.getBroadcast(context, cutoff.id,
                alarmIntent(context, cutoff).putExtra(EXTRA_SCHEDULED_AT, next),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm);
            preferences.edit().putLong(key(NEXT, cutoff), next).apply();
            Log.i(TAG, "Scheduled " + cutoff + " standby for " + Instant.ofEpochMilli(next).atZone(now.getZone()));
        } catch (SecurityException unavailable) {
            alarms.cancel(alarm);
            preferences.edit().putLong(key(NEXT, cutoff), 0).apply();
            Log.e(TAG, "Exact daily standby alarm was refused", unavailable);
            failure(context, R.string.standby_alarm_failed);
        }
    }

    static String status(Context context) {
        int problem = 0;
        if (Build.VERSION.SDK_INT < 28) problem = R.string.standby_unsupported;
        else if (!canSchedule(context)) problem = R.string.standby_alarm_access_needed;
        else if (!HomeKeyService.isEnabled(context)) problem = R.string.standby_helper_needed;
        else if (!HomeKeyService.isConnected()) problem = R.string.standby_helper_connecting;
        StringBuilder deadlines = new StringBuilder();
        for (Cutoff cutoff : Cutoff.values()) {
            long next = store(context).getLong(key(NEXT, cutoff), 0);
            if (next <= System.currentTimeMillis()) {
                if (problem == 0) problem = R.string.standby_not_scheduled;
            } else {
                String date = DateTimeFormatter.ofPattern("EEE, d MMM, h:mm a z", Locale.getDefault())
                        .format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()));
                deadlines.append("\n\n").append(context.getString(cutoff == Cutoff.WEEKDAYS
                        ? R.string.standby_weekday_next : R.string.standby_everyday_next, date));
            }
        }
        String text = problem != 0 ? context.getString(problem) : context.getString(
                skippedToday(context) ? R.string.standby_skipped : R.string.standby_ready) + deadlines;
        String error = store(context).getString(ERROR, "");
        return error == null || error.isEmpty() ? text
                : text + "\n\n" + context.getString(R.string.standby_last_failure, error);
    }

    static void fire(Context context, long scheduledAt, Cutoff cutoff) {
        SharedPreferences preferences = store(context);
        ZonedDateTime now = ZonedDateTime.now();
        if (scheduledAt != preferences.getLong(key(NEXT, cutoff), 0)) {
            Log.w(TAG, "Ignored a superseded daily standby alarm");
            return;
        }
        boolean due = DailyStandby.isDue(now, scheduledAt, preferences.getString(SKIP, ""),
                preferences.getString(key(HANDLED, cutoff), ""), cutoff);
        if (!due) {
            Log.i(TAG, "Skipped early, missed, duplicate or disabled daily standby alarm");
            schedule(context, false, cutoff);
            return;
        }
        if (!preferences.edit().putString(key(HANDLED, cutoff), now.toLocalDate().toString()).commit()) {
            schedule(context, false, cutoff);
            failure(context, R.string.standby_save_failed);
            return;
        }
        schedule(context, false, cutoff);
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null) {
            failure(context, R.string.standby_action_failed);
            return;
        }
        if (!power.isInteractive()) {
            Log.i(TAG, cutoff + ": TV is already asleep; no screen wake or power toggle");
        } else if (!HomeKeyService.standby()) {
            failure(context, R.string.standby_action_failed);
            return;
        } else {
            Log.i(TAG, cutoff + " standby requested through the accessibility helper");
        }
        preferences.edit().remove(ERROR).apply();
    }

    private static void failure(Context context, int message) {
        String text = context.getString(message);
        Log.e(TAG, text);
        store(context).edit().putString(ERROR, text).apply();
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
