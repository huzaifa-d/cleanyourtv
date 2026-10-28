package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CherryBlossomTest {
    @Before public void stillDefault() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("aurora", 0)
                .edit().clear().commit();
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    @Test public void pulseDrawsBelowTheLogoAndFocusChangesInactivityAndDetachCancelIt() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                AuroraHomeView home = (AuroraHomeView) field(activity, "home");
                BlossomTile tile = activity.findViewById(R.id.netflix);
                BlossomTile next = activity.findViewById(R.id.prime);
                tile.requestFocus();
                ValueAnimator animation = (ValueAnimator) field(tile, "focusAnimator");
                assertEquals(700, animation.getDuration());
                animation.setCurrentPlayTime(350);
                assertEquals(.12f, (float) field(tile, "pulse"), .001f);
                tile.flower.animate().cancel();
                tile.flower.setRotation(-20f);
                Bitmap glowing = draw(tile);
                animation.setCurrentPlayTime(700);
                assertEquals(0f, (float) field(tile, "pulse"), 0f);
                Bitmap settled = draw(tile);
                assertFalse("The native Canvas must actually draw the subtle pulse",
                        glowing.sameAs(settled));
                glowing.recycle();
                settled.recycle();
                assertEquals(0f, tile.mark.getRotation(), 0f);

                next.requestFocus();
                tile.requestFocus();
                animation.setCurrentPlayTime(350);
                float currentScale = tile.getScaleX();
                next.requestFocus();
                assertFalse(animation.isStarted());
                assertEquals(0f, (float) field(tile, "pulse"), 0f);
                tile.requestFocus();
                assertEquals("Interrupted re-entry must not snap back to 100%",
                        currentScale, tile.getScaleX(), .001f);
                animation.setCurrentPlayTime(350);
                home.setHomeActive(false);
                assertFalse(animation.isStarted());
                assertEquals(0f, (float) field(tile, "pulse"), 0f);
                assertEquals(1.14f, tile.getScaleX(), .001f);
                assertEquals(1f, next.getScaleX(), .001f);
                home.setHomeActive(true);
                assertFalse("Returning Home must not repeat an already focused effect", animation.isStarted());
                next.requestFocus();
                tile.requestFocus();
                assertTrue(animation.isStarted());
                FrameLayout composition = activity.findViewById(R.id.home_root);
                composition.removeView(tile);
                assertFalse(animation.isStarted());
                assertEquals(0f, (float) field(tile, "pulse"), 0f);
            });
        }
    }

    private void animationScale(String value) throws Exception {
        String command = value == null ? "settings delete global animator_duration_scale"
                : "settings put global animator_duration_scale " + Float.parseFloat(value);
        try (ParcelFileDescriptor.AutoCloseInputStream output = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[256];
            while (output.read(buffer) != -1) { }
        }
        boolean enabled = value == null || Float.parseFloat(value) > 0;
        for (int attempt = 0; attempt < 100 && ValueAnimator.areAnimatorsEnabled() != enabled; attempt++)
            SystemClock.sleep(20);
        assertEquals(enabled, ValueAnimator.areAnimatorsEnabled());
    }

    @Test public void disabledSystemAnimationAppliesFinalFocusWithoutPulseOrTurnMotion() throws Exception {
        String previous = Float.toString(Settings.Global.getFloat(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f));
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            try {
                animationScale("0");
                scene.onActivity(activity -> {
                    BlossomTile tile = activity.findViewById(R.id.netflix);
                    tile.requestFocus();
                    assertEquals(1.14f, tile.getScaleX(), .001f);
                    assertEquals(-20f, tile.flower.getRotation(), .001f);
                    assertEquals(0f, (float) field(tile, "pulse"), 0f);
                    assertFalse(((ValueAnimator) field(tile, "focusAnimator")).isStarted());
                    activity.findViewById(R.id.prime).requestFocus();
                    assertEquals(1f, tile.getScaleX(), .001f);
                    assertEquals(-28f, tile.flower.getRotation(), .001f);
                });
            } finally {
                animationScale(previous);
            }
        }
    }

    private void key(int code) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                .perform(androidx.test.espresso.action.ViewActions.pressKey(code));
    }

    @Test public void focusRisesPastItsRestingSizeThenSettlesWithoutRepeating() throws Exception {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            CountDownLatch finished = new CountDownLatch(1);
            float[] peak = {1f};
            scene.onActivity(activity -> {
                BlossomTile tile = activity.findViewById(R.id.netflix);
                long started = SystemClock.uptimeMillis();
                tile.postOnAnimation(new Runnable() {
                    @Override public void run() {
                        peak[0] = Math.max(peak[0], tile.getScaleX());
                        if (SystemClock.uptimeMillis() - started < 1100) tile.postOnAnimation(this);
                        else finished.countDown();
                    }
                });
            });
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertTrue("Focus frames must complete", finished.await(5, TimeUnit.SECONDS));
            assertEquals("G5 must visibly overshoot before settling", 1.17f, peak[0], .003f);
            scene.onActivity(activity -> {
                BlossomTile tile = activity.findViewById(R.id.netflix);
                assertTrue(tile.hasFocus());
                assertEquals(1.14f, tile.getScaleX(), .001f);
                assertEquals(1.14f, tile.getScaleY(), .001f);
                assertEquals(0f, tile.mark.getRotation(), 0f);
            });
            SystemClock.sleep(2100);
            scene.onActivity(activity ->
                    assertEquals("The review-only repetition must not ship", 1.14f,
                            activity.findViewById(R.id.netflix).getScaleX(), .001f));
        }
    }

    @Test public void actualSpritesAdvanceOnlyOnActiveHomeAndFreezeBehindPanelsAndWhenStopped() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            AtomicReference<PetalView> owner = new AtomicReference<>();
            Bitmap[] images = new Bitmap[2];
            long[] elapsed = new long[1];
            scene.onActivity(activity -> {
                PetalView petals = activity.findViewById(R.id.petals);
                owner.set(petals);
                assertTrue((boolean) field(petals, "running"));
                Bitmap[] textures = (Bitmap[]) field(petals, "textures");
                assertEquals(6, textures.length);
                for (Bitmap texture : textures) {
                    assertNotNull(texture);
                    assertTrue(texture.getWidth() >= 339);
                }
                images[0] = draw(petals);
                elapsed[0] = (long) field(petals, "elapsedMillis");
            });
            SystemClock.sleep(600);
            scene.onActivity(activity -> {
                assertTrue((long) field(owner.get(), "elapsedMillis") > elapsed[0]);
                images[1] = draw(owner.get());
                assertFalse("Native textured petals must visibly move", images[0].sameAs(images[1]));
                images[0].recycle();
                images[1].recycle();
            });
            key(KeyEvent.KEYCODE_MENU);
            scene.onActivity(activity -> {
                assertFalse((boolean) field(owner.get(), "running"));
                elapsed[0] = (long) field(owner.get(), "elapsedMillis");
                images[0] = draw(owner.get());
            });
            SystemClock.sleep(600);
            scene.onActivity(activity -> {
                assertEquals(elapsed[0], field(owner.get(), "elapsedMillis"));
                images[1] = draw(owner.get());
                assertTrue(images[0].sameAs(images[1]));
                images[0].recycle();
                images[1].recycle();
            });
            key(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(300);
            scene.onActivity(activity -> {
                assertTrue((boolean) field(owner.get(), "running"));
                assertTrue((long) field(owner.get(), "elapsedMillis") > elapsed[0]);
            });
            scene.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertFalse((boolean) field(owner.get(), "running"));
                elapsed[0] = (long) field(owner.get(), "elapsedMillis");
            });
            SystemClock.sleep(600);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    assertEquals(elapsed[0], field(owner.get(), "elapsedMillis")));
            scene.moveToState(Lifecycle.State.RESUMED);
            SystemClock.sleep(300);
            scene.onActivity(activity -> assertTrue((long) field(owner.get(), "elapsedMillis") > elapsed[0]));
        }
    }

    @Test public void activationBeforeAttachmentWaitsAndDetachmentCancelsFramesWithoutPhaseJump() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            AtomicReference<PetalView> owner = new AtomicReference<>();
            long[] elapsed = new long[1];
            scene.onActivity(activity -> {
                PetalView petals = new PetalView(activity);
                owner.set(petals);
                petals.setActive(true);
                assertFalse((boolean) field(petals, "running"));
                FrameLayout composition = activity.findViewById(R.id.home_root);
                composition.addView(petals, new FrameLayout.LayoutParams(960, 540));
                assertTrue((boolean) field(petals, "running"));
            });
            SystemClock.sleep(300);
            scene.onActivity(activity -> {
                assertTrue((long) field(owner.get(), "elapsedMillis") > 0);
                FrameLayout composition = activity.findViewById(R.id.home_root);
                composition.removeView(owner.get());
                assertFalse((boolean) field(owner.get(), "running"));
                elapsed[0] = (long) field(owner.get(), "elapsedMillis");
            });
            SystemClock.sleep(800);
            scene.onActivity(activity -> {
                assertEquals(elapsed[0], field(owner.get(), "elapsedMillis"));
                FrameLayout composition = activity.findViewById(R.id.home_root);
                composition.addView(owner.get(), new FrameLayout.LayoutParams(960, 540));
                assertTrue((boolean) field(owner.get(), "running"));
                assertEquals("Hidden time must not jump the particle phase",
                        elapsed[0], field(owner.get(), "elapsedMillis"));
            });
        }
    }
}
