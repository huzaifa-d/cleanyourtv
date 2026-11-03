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

    @Test public void startsWithTenSecondUrduThenFifteenSecondEnglishAndResetsAfterPanel() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> assertEquals(activity.getString(expected(true)),
                    ((TextView) activity.findViewById(R.id.greeting)).getText()));
            awaitLanguage(scene, true);
            long start = SystemClock.uptimeMillis();
            SystemClock.sleep(8500);
            scene.onActivity(activity -> assertEquals(activity.getString(expected(true)),
                    ((TextView) activity.findViewById(R.id.greeting)).getText()));
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
            assertTrue("Urdu must remain first for ten seconds", elapsed >= 9500);
            assertTrue("Urdu must not stay visible for fifteen seconds", elapsed < 12500);
            SystemClock.sleep(11000);
            scene.onActivity(activity -> assertEquals(activity.getString(expected(false)),
                    ((TextView) activity.findViewById(R.id.greeting)).getText()));
            awaitLanguage(scene, true);
            elapsed = SystemClock.uptimeMillis() - start;
            assertTrue("English must last fifteen seconds", elapsed >= 24500);
            assertTrue("The full cycle must not stretch beyond twenty-five seconds", elapsed < 27500);
            SystemClock.sleep(8500);
            scene.onActivity(activity -> assertEquals(activity.getString(expected(true)),
                    ((TextView) activity.findViewById(R.id.greeting)).getText()));
            awaitLanguage(scene, false);
            assertTrue("Subsequent Urdu intervals must also last ten seconds",
                    SystemClock.uptimeMillis() - start >= 34500);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_MENU));
            scene.onActivity(activity -> {
                assertEquals(Boolean.FALSE, field(field(activity, "home"), "greetingActive"));
                assertEquals(1f, activity.findViewById(R.id.greeting).getAlpha(), 0f);
            });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_BACK));
            awaitLanguage(scene, true);
            scene.onActivity(activity -> assertEquals(Boolean.TRUE,
                    field(field(activity, "home"), "greetingActive")));
        }
    }
}
