package com.gathin.tvhome;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DailyStandbyMenuTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private void menu() {
        onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_MENU));
        onView(withText(R.string.daily_standby)).perform(scrollTo(), click());
    }

    @Test public void menuPersistsSkipWithoutClearingOtherPreferences() {
        String original = StandbyScheduler.store(context).getString("skipped_date", "");
        boolean initiallySkipped = StandbyScheduler.skippedToday(context);
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withText(R.string.standby_description)).check(matches(isDisplayed()));
            onView(withId(R.id.standby_skip_today)).perform(scrollTo(), click());
            assertEquals(!initiallySkipped, StandbyScheduler.skippedToday(context));
            pressBack();
            pressBack();
            scene.recreate();
            menu();
            onView(withId(R.id.standby_skip_today))
                    .check(matches(initiallySkipped ? isNotChecked() : isChecked()));
            onView(withId(R.id.standby_status)).check(matches(isDisplayed()));
        } finally {
            assertTrue(StandbyScheduler.store(context).edit().putString("skipped_date", original).commit());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> StandbyScheduler.reschedule(context));
        }
    }

    @Test public void alarmReceiverIsPrivateAndRecoveryPermissionsAreDeclared() throws Exception {
        assertFalse(context.getPackageManager().getReceiverInfo(
                new ComponentName(context, StandbyReceiver.class), 0).exported);
        String[] permissions = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertTrue(java.util.Arrays.asList(permissions).contains("android.permission.SCHEDULE_EXACT_ALARM"));
        assertTrue(java.util.Arrays.asList(permissions).contains("android.permission.RECEIVE_BOOT_COMPLETED"));
    }

    @Test public void cutoffRequestCodesCreateIndependentPendingIntents() {
        Intent test = new Intent(context, StandbyReceiver.class)
                .setAction(StandbyScheduler.ACTION_FIRE + ".IDENTITY_TEST");
        PendingIntent weekday = PendingIntent.getBroadcast(context, DailyStandby.Cutoff.WEEKDAYS.id,
                test, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent everyday = PendingIntent.getBroadcast(context, DailyStandby.Cutoff.EVERY_DAY.id,
                test, PendingIntent.FLAG_IMMUTABLE);
        try {
            assertNotEquals(weekday, everyday);
        } finally {
            weekday.cancel();
            everyday.cancel();
        }
    }
}
