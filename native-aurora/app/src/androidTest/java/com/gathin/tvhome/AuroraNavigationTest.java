package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraNavigationTest {
    @Before public void stillFallback() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("aurora", 0)
                .edit().clear().putString("video", "ui-test-missing.mp4").commit();
    }
    @After public void cleanup() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("aurora", 0)
                .edit().clear().commit();
    }
    private void key(int code) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                .perform(androidx.test.espresso.action.ViewActions.pressKey(code));
    }

    @Test public void initialDirectionAndBackMatchApprovedPolicy() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.home_root).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.all_apps).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.all_apps).hasFocus()));
            key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.netflix).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_UP);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.profile).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.prime).hasFocus()));
        }
    }
}
