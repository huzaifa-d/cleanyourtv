package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraGreetingTest {
    private static int expected(boolean urdu) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return urdu ? hour < 12 ? R.string.good_morning_urdu
                : hour < 18 ? R.string.good_afternoon_urdu : R.string.good_evening_urdu
                : hour < 12 ? R.string.good_morning
                : hour < 18 ? R.string.good_afternoon : R.string.good_evening;
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void awaitLanguage(ActivityScenario<AuroraActivity> scene, boolean urdu) {
        long deadline = SystemClock.uptimeMillis() + 17000;
        AtomicBoolean met = new AtomicBoolean();
        do {
            scene.onActivity(activity -> {
                TextView greeting = activity.findViewById(R.id.greeting);
                met.set(greeting.getText().equals(activity.getString(expected(urdu)))
                        && greeting.getAlpha() == 1f && greeting.getTranslationY() == 0f);
            });
            if (met.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Greeting did not complete its scheduled language transition");
    }

    @Test public void alternatesFifteenSecondEnglishAndTenSecondUrduAndResetsAfterPanel() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            awaitLanguage(scene, false);
            long start = SystemClock.uptimeMillis();
            SystemClock.sleep(13500);
            scene.onActivity(activity -> assertEquals(activity.getString(expected(false)),
                    ((TextView) activity.findViewById(R.id.greeting)).getText()));
            awaitLanguage(scene, true);
            assertTrue(SystemClock.uptimeMillis() - start >= 14500);
            scene.onActivity(activity -> {
                AuroraHomeView home = (AuroraHomeView) field(activity, "home");
                TextView greeting = activity.findViewById(R.id.greeting);
                assertEquals(activity.getString(expected(true)), greeting.getText());
                assertEquals(View.TEXT_DIRECTION_RTL, greeting.getTextDirection());
                assertTrue(greeting.getPaint().hasGlyph("ص"));
                assertTrue(greeting.getPaint().hasGlyph("ی"));
                assertNotNull(greeting.getLayout());
                assertEquals(1, greeting.getLineCount());
                assertTrue(greeting.getLayout().getHeight() <= greeting.getHeight());
                home.updateClock();
                assertEquals(activity.getString(expected(true)), greeting.getText());
            });
            awaitLanguage(scene, false);
            long elapsed = SystemClock.uptimeMillis() - start;
            assertTrue("Must return after the ten-second Urdu interval", elapsed >= 24500);
            assertTrue("Urdu must not stay visible for fifteen seconds", elapsed < 27500);
            SystemClock.sleep(11000);
            scene.onActivity(activity -> assertEquals(activity.getString(expected(false)),
                    ((TextView) activity.findViewById(R.id.greeting)).getText()));
            awaitLanguage(scene, true);
            assertTrue("Subsequent English intervals must also last fifteen seconds",
                    SystemClock.uptimeMillis() - start >= 39500);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_MENU));
            scene.onActivity(activity -> {
                assertEquals(Boolean.FALSE, field(field(activity, "home"), "greetingActive"));
                assertEquals(1f, activity.findViewById(R.id.greeting).getAlpha(), 0f);
            });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_BACK));
            awaitLanguage(scene, false);
            scene.onActivity(activity -> assertEquals(Boolean.TRUE,
                    field(field(activity, "home"), "greetingActive")));
        }
    }
}
