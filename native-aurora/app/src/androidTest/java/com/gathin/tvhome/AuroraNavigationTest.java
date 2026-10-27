package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;
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

    @Test public void sevenBorderlessIconsGrowWithoutClippingOrOverlap() {
        int[] ids = {R.id.netflix, R.id.prime, R.id.youtube, R.id.disney,
                R.id.hotstar, R.id.apple_tv, R.id.all_apps};
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            Drawable[] idleApple = new Drawable[1];
            scene.onActivity(activity -> {
                for (int id : ids) {
                    ViewGroup tile = activity.findViewById(id);
                    assertNull(tile.getBackground());
                    ImageView mark = (ImageView) tile.getChildAt(0);
                    assertNotNull(mark.getDrawable());
                    assertEquals(1.5f, mark.getScaleX(), 0.001f);
                    assertEquals(1.5f, mark.getScaleY(), 0.001f);
                }
                ViewGroup apple = activity.findViewById(R.id.apple_tv);
                assertEquals("Apple TV", apple.getContentDescription().toString());
                idleApple[0] = ((ImageView) apple.getChildAt(0)).getDrawable();
            });
            for (int focused = 0; focused < ids.length; focused++) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT);
                final int current = focused;
                scene.onActivity(activity -> {
                    ViewGroup tile = activity.findViewById(ids[current]);
                    assertTrue(tile.hasFocus());
                    assertNull(tile.getBackground());
                    float previousRight = 0;
                    for (int index = 0; index < ids.length; index++) {
                        ViewGroup item = activity.findViewById(ids[index]);
                        ImageView mark = (ImageView) item.getChildAt(0);
                        float scale = index == current ? 2f : 1.5f;
                        assertEquals(scale, mark.getScaleX(), 0.001f);
                        assertEquals(scale, mark.getScaleY(), 0.001f);
                        float centre = item.getLeft() + mark.getLeft() + mark.getWidth() / 2f;
                        float halfWidth = mark.getWidth() * scale / 2f;
                        assertTrue(centre - halfWidth >= previousRight);
                        assertTrue(centre + halfWidth <= 960);
                        assertTrue(item.getTop() + mark.getTop()
                                + mark.getHeight() / 2f - mark.getHeight() * scale / 2f >= 0);
                        assertTrue(item.getTop() + mark.getTop()
                                + mark.getHeight() / 2f + mark.getHeight() * scale / 2f <= 540);
                        previousRight = centre + halfWidth;
                    }
                    if (current == 5) assertNotSame(idleApple[0],
                            ((ImageView) tile.getChildAt(0)).getDrawable());
                });
            }
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.apple_tv).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_UP);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.apple_tv).hasFocus()));
            key(KeyEvent.KEYCODE_BACK);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.home_root).hasFocus()));
        }
    }
}
