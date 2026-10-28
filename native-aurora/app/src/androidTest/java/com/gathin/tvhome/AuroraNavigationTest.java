package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.view.KeyEvent;
import android.os.SystemClock;
import android.graphics.drawable.BitmapDrawable;
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

    @Test public void sevenBlossomsFollowBothRowsGrowAndTurnWithUprightMarks() {
        int[] ids = {R.id.netflix, R.id.prime, R.id.youtube, R.id.disney,
                R.id.hotstar, R.id.apple_tv, R.id.all_apps};
        int[] angles = {-28, 46, -84, 118, -142, 76, 164};
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            Drawable[] idleApple = new Drawable[1];
            scene.onActivity(activity -> {
                Object sharedBlossom = null;
                for (int id : ids) {
                    BlossomTile tile = activity.findViewById(id);
                    assertNull(tile.getBackground());
                    assertNotNull(tile.mark.getDrawable());
                    assertEquals(1f, tile.getScaleX(), .001f);
                    assertEquals(1f, tile.getScaleY(), .001f);
                    Object bitmap = ((BitmapDrawable) tile.flower.getDrawable()).getBitmap();
                    if (sharedBlossom == null) sharedBlossom = bitmap;
                    else assertSame("Seven buttons must share one native texture", sharedBlossom, bitmap);
                }
                BlossomTile apple = activity.findViewById(R.id.apple_tv);
                BlossomTile hotstar = activity.findViewById(R.id.hotstar);
                assertTrue("Apple TV must occupy the left blossom, with JioHotstar in the middle",
                        apple.getLeft() < hotstar.getLeft());
                assertEquals("Apple TV", apple.getContentDescription().toString());
                idleApple[0] = apple.mark.getDrawable();
            });
            int[] order = {0, 1, 2, 3, 6, 4, 5};
            int[] keys = {KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT};
            for (int step = 0; step < order.length; step++) {
                key(keys[step]);
                SystemClock.sleep(850);
                final int current = order[step];
                scene.onActivity(activity -> {
                    BlossomTile tile = activity.findViewById(ids[current]);
                    assertTrue(tile.hasFocus());
                    assertNull(tile.getBackground());
                    for (int index = 0; index < ids.length; index++) {
                        BlossomTile item = activity.findViewById(ids[index]);
                        float scale = index == current ? 1.14f : 1f;
                        assertEquals(scale, item.getScaleX(), .001f);
                        assertEquals(scale, item.getScaleY(), .001f);
                        assertEquals(angles[index] + (index == current ? 8 : 0),
                                item.flower.getRotation(), .001f);
                        assertEquals(0, item.mark.getRotation(), 0);
                        assertEquals(1, item.mark.getScaleX(), 0);
                        float x = item.getLeft() + item.getWidth() / 2f;
                        float y = item.getTop() + item.getHeight() / 2f;
                        float radius = item.getWidth() * scale / 2f;
                        assertTrue(x - radius > 0 && x + radius < 960);
                        assertTrue(y - radius > 0 && y + radius < 540);
                        for (int other = index + 1; other < ids.length; other++) {
                            BlossomTile neighbour = activity.findViewById(ids[other]);
                            float dx = x - neighbour.getLeft() - neighbour.getWidth() / 2f;
                            float dy = y - neighbour.getTop() - neighbour.getHeight() / 2f;
                            assertTrue("Blossoms must leave room for focus growth",
                                    Math.hypot(dx, dy) > radius + neighbour.getWidth()
                                            * neighbour.getScaleX() / 2f);
                        }
                    }
                    if (current == 5) assertNotSame(idleApple[0], tile.mark.getDrawable());
                });
            }
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.hotstar).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_UP);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.youtube).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_UP);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.youtube).hasFocus()));
            key(KeyEvent.KEYCODE_BACK);
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.home_root).hasFocus()));
        }
    }
}
