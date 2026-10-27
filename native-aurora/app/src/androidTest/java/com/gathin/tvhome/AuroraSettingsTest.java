package com.gathin.tvhome;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraSettingsTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Before public void reset() {
        context.getSharedPreferences("aurora", 0).edit().clear().putString("video", "ui-test-missing.mp4").commit();
    }
    @After public void cleanup() { context.getSharedPreferences("aurora", 0).edit().clear().commit(); }
    private void menu() {
        onView(isRoot()).perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_MENU));
    }
    @Test public void ramAndNestedBackPersistWithoutSpeedSelectorAndRestoreProfile() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withId(R.id.ram_switch)).check(matches(isChecked())).perform(click());
            onView(withText(R.string.background)).perform(click());
            onView(withText(org.hamcrest.Matchers.startsWith("Playback speed"))).check(doesNotExist());
            onView(withText(R.string.default_background)).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.ram_switch)).check(matches(isNotChecked()));
            pressBack();
            scene.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.profile).hasFocus());
                assertEquals(View.GONE, activity.findViewById(R.id.memory).getVisibility());
            });
            scene.recreate();
            menu();
            onView(withId(R.id.ram_switch)).check(matches(isNotChecked()));
            onView(withText(R.string.background)).perform(click());
            onView(withText(org.hamcrest.Matchers.startsWith("Playback speed"))).check(doesNotExist());
            onView(withText(R.string.default_background)).check(matches(isDisplayed()));
            pressBack();
            pressBack();
        }
    }
    @Test public void refreshFindsCompleteMp4AndSelectionSurvivesRecreation() throws Exception {
        File directory = new File(context.getExternalFilesDir(null), "backgrounds");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File completed = new File(directory, "settings-test.mp4");
        File partial = new File(directory, "settings-test.mp4.part");
        try {
            assertTrue(partial.createNewFile());
            try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
                menu();
                onView(withText(R.string.background)).perform(click());
                onView(withText(partial.getName())).check(doesNotExist());
                assertTrue(completed.createNewFile());
                onView(withText(R.string.refresh)).perform(click());
                onView(withText(completed.getName())).perform(scrollTo(), click());
                assertEquals(completed.getName(), new AuroraPreferences(
                        context.getSharedPreferences("aurora", 0)).video());
                pressBack();
                pressBack();
                scene.recreate();
                menu();
                onView(withText(R.string.background)).perform(click());
                onView(withText(R.string.default_background)).perform(scrollTo(), click());
                assertEquals("", new AuroraPreferences(context.getSharedPreferences("aurora", 0)).video());
            }
        } finally {
            completed.delete();
            partial.delete();
        }
    }
}
