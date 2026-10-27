package com.gathin.tvhome;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
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
}
