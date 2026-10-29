package com.gathin.tvhome;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraLifecycleTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Before public void stillAndRamDisabled() {
        context.getSharedPreferences("aurora", 0).edit().clear()
                .putString("video", "lifecycle-missing.mp4").putBoolean("show_ram", false).commit();
    }
    @After public void cleanup() { context.getSharedPreferences("aurora", 0).edit().clear().commit(); }
    private void key(int code) {
        onView(isRoot()).perform(androidx.test.espresso.action.ViewActions.pressKey(code));
    }
    private long dedicationRemaining(android.app.Activity activity) {
        android.os.Bundle snapshot = new android.os.Bundle();
        ((AuroraHomeView) activity.findViewById(R.id.home_root).getParent()).saveState(snapshot);
        return snapshot.getLong("dedication_remaining");
    }
    @Test public void rowFocusSurvivesRecreationAndStoppedHome() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            scene.recreate();
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.prime).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_UP);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.prime).hasFocus()));
            scene.moveToState(Lifecycle.State.CREATED);
            scene.moveToState(Lifecycle.State.RESUMED);
            scene.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.prime).hasFocus());
                assertEquals(View.GONE, activity.findViewById(R.id.memory).getVisibility());
            });
        }
    }
    @Test public void repeatedNestedPanelsAndLibraryReturnToInvokingControl() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            for (int i = 0; i < 3; i++) {
                key(KeyEvent.KEYCODE_MENU);
                onView(withText(R.string.background)).perform(click());
                scene.onActivity(activity -> assertEquals(
                        View.VISIBLE, activity.findViewById(R.id.background_still).getVisibility()));
                pressBack();
                onView(withId(R.id.ram_switch)).check(matches(isNotChecked()));
                pressBack();
                scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.profile).hasFocus()));
            }
            key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()));
            pressBack();
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.all_apps).hasFocus()));
        }
    }

    @Test public void dedicationWaitsForVisibleHomeAndSurvivesRecreationWithoutReplaying() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                View caption = activity.findViewById(R.id.dedication);
                assertNotNull("Dedication belongs to Home, not the settings drawer", caption);
                assertEquals(View.INVISIBLE, caption.getVisibility());
                assertTrue("Dedication must be faint", caption.getAlpha() > 0 && caption.getAlpha() <= .35f);
                assertFalse(caption.isFocusable());
                int[] location = new int[2];
                caption.getLocationOnScreen(location);
                assertTrue(location[0] < activity.getResources().getDisplayMetrics().widthPixels / 10);
                assertTrue(location[1] > activity.getResources().getDisplayMetrics().heightPixels * .9f);
            });
            android.os.SystemClock.sleep(6000);
            scene.onActivity(activity -> {
                assertEquals(View.INVISIBLE, activity.findViewById(R.id.dedication).getVisibility());
                assertTrue("First visible Home time must count; remainder " + dedicationRemaining(activity)
                        + ", focus " + activity.hasWindowFocus(), dedicationRemaining(activity) < 5000);
            });
            key(KeyEvent.KEYCODE_MENU);
            onView(withText(R.string.created_with_love)).check(doesNotExist());
            android.os.SystemClock.sleep(10500);
            scene.onActivity(activity -> assertEquals("Time behind the drawer must not count",
                    View.INVISIBLE, activity.findViewById(R.id.dedication).getVisibility()));
            pressBack();
            scene.recreate();
            scene.onActivity(activity -> assertTrue("Recreation must restore remainder: "
                    + dedicationRemaining(activity), dedicationRemaining(activity) < 5000));
            android.os.SystemClock.sleep(4700);
            scene.onActivity(activity -> assertEquals("Recreation must retain already-visible Home time; remainder "
                            + dedicationRemaining(activity) + ", focus " + activity.hasWindowFocus(),
                    View.VISIBLE, activity.findViewById(R.id.dedication).getVisibility()));
            scene.recreate();
            scene.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.dedication).getVisibility()));
            scene.moveToState(Lifecycle.State.CREATED);
            scene.moveToState(Lifecycle.State.RESUMED);
            scene.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.dedication).getVisibility()));
        }
        try (ActivityScenario<AuroraActivity> restarted = ActivityScenario.launch(AuroraActivity.class)) {
            restarted.onActivity(activity -> assertEquals("A new launcher start gets its own delay",
                    View.INVISIBLE, activity.findViewById(R.id.dedication).getVisibility()));
        }
    }
}
