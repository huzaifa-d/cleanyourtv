package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeBackgroundTest {
    @Before public void defaultBackground() {
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

    private long time(String utc) { return Instant.parse(utc).toEpochMilli(); }

    private ThemeBackgroundView attach(AuroraActivity activity, AtomicLong clock) {
        return attach(activity, clock, null);
    }

    private ThemeBackgroundView attach(AuroraActivity activity, AtomicLong clock, Bitmap texture) {
        ((AuroraHomeView) field(activity, "home")).setHomeActive(false);
        ThemeBackgroundView view = new ThemeBackgroundView(activity, clock::get, () -> ZoneId.of("UTC"), texture);
        FrameLayout root = activity.findViewById(R.id.home_root);
        root.removeView(activity.findViewById(R.id.background_still));
        root.addView(view, 0, new FrameLayout.LayoutParams(960, 540));
        view.setOutputWidth(1920);
        return view;
    }

    private Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private void save(Bitmap image, String name) {
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), name + ".png");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private double distance(int colour, int other) {
        return Math.abs(Color.red(colour) - Color.red(other))
                + Math.abs(Color.green(colour) - Color.green(other))
                + Math.abs(Color.blue(colour) - Color.blue(other));
    }

    private double contrast(Bitmap sample, Bitmap bare, Bitmap full, int x, int y) {
        return distance(sample.getPixel(x, y), bare.getPixel(x, y))
                / Math.max(1, distance(full.getPixel(x, y), bare.getPixel(x, y)));
    }

    private Bitmap hardwareMoon(ThemeBackgroundView view, int scale) throws InterruptedException {
        return hardwareRegion(view, scale, ThemeBackgroundView.MOON_X - 64,
                ThemeBackgroundView.MOON_Y - 64, 128);
    }

    static Bitmap hardwareRegion(View view, int scale, float left, float top, int extent)
            throws InterruptedException {
        int size = extent * scale;
        HandlerThread thread = new HandlerThread("lunar-capture");
        thread.start();
        HardwareRenderer renderer = new HardwareRenderer();
        try (ImageReader reader = ImageReader.newInstance(size, size, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)) {
            CountDownLatch ready = new CountDownLatch(1);
            reader.setOnImageAvailableListener(owner -> ready.countDown(), new Handler(thread.getLooper()));
            RenderNode node = new RenderNode("lunar-edge-fixture");
            node.setPosition(0, 0, size, size);
            Canvas canvas = node.beginRecording();
            canvas.scale(scale, scale);
            canvas.translate(-left, -top);
            view.draw(canvas);
            node.endRecording();
            renderer.setOpaque(false);
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            assertTrue("GPU frame must become available", ready.await(5, TimeUnit.SECONDS));
            try (Image image = reader.acquireLatestImage();
                    HardwareBuffer buffer = image.getHardwareBuffer()) {
                Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                assertNotNull(hardware);
                Bitmap result = hardware.copy(Bitmap.Config.ARGB_8888, false);
                hardware.recycle();
                return result;
            }
        } finally {
            renderer.destroy();
            thread.quitSafely();
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test public void nativeLunarEdgesMatchSubpixelSphericalCoverageAtOneAndFourTimesOutput() {
        try (ActivityScenario<AuroraActivity> scenario = ActivityScenario.launch(AuroraActivity.class)) {
            scenario.onActivity(activity -> {
                Bitmap white = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
                white.eraseColor(Color.WHITE);
                ThemeBackgroundView view = attach(activity,
                        new AtomicLong(time("2026-03-22T20:00:00Z")), white);
                view.layout(0, 0, 960, 540);
                try {
                    java.lang.reflect.Constructor<MoonPhase> phase =
                            MoonPhase.class.getDeclaredConstructor(double.class, boolean.class);
                    phase.setAccessible(true);
                    view.scene.introMillis = 5000;
                    for (int scale : new int[] {1, 4}) {
                        view.setOutputWidth(960 * scale);
                        for (String name : new String[] {"day", "night", "dayForeground", "nightForeground"}) {
                            Field target = ThemeBackgroundView.class.getDeclaredField(name);
                            target.setAccessible(true);
                            target.set(view, null);
                        }
                        for (double fraction : new double[] {.2, .5, .8, 1}) {
                            boolean waxing = fraction <= .5;
                            view.scene.phase = phase.newInstance(fraction, waxing);
                            Bitmap image = hardwareMoon(view, scale);
                            double error = 0;
                            int edgePixels = 0;
                            double radius = ThemeBackgroundView.MOON_RADIUS;
                            for (int y = (int) ((64 - radius + 1) * scale);
                                    y < (64 + radius - 1) * scale; y++) {
                                for (int x = (int) ((64 - radius + 1) * scale);
                                        x < (64 + radius - 1) * scale; x++) {
                                    int inside = 0;
                                    for (int sy = 0; sy < 12; sy++) for (int sx = 0; sx < 12; sx++) {
                                        double py = ((y + (sy + .5) / 12) / scale - 64) / radius;
                                        double px = ((x + (sx + .5) / 12) / scale - 64) / radius;
                                        double limb = Math.sqrt(Math.max(0, 1 - py * py));
                                        double left = waxing ? -limb : -(2 * fraction - 1) * limb;
                                        double right = waxing ? (2 * fraction - 1) * limb : limb;
                                        if (Math.abs(py) < 1 && px >= left && px <= right) inside++;
                                    }
                                    double coverage = inside / 144.0;
                                    if (coverage <= .1 || coverage >= .9) continue;
                                    error += Math.abs((Color.alpha(image.getPixel(x, y)) - 26) / 229.0
                                            - coverage);
                                    edgePixels++;
                                }
                            }
                            assertTrue("Fixture must sample genuine subpixel edges", edgePixels > 10);
                            save(image, "lunar-smooth-" + scale + "-" + fraction);
                            assertTrue("Jagged spherical edge at scale " + scale + ", phase "
                                    + fraction + ": mean coverage error " + error / edgePixels
                                    + ", corner " + Integer.toHexString(image.getPixel(0, 0))
                                    + ", centre " + Integer.toHexString(image.getPixel(64 * scale, 64 * scale)),
                                    error / edgePixels < .12);
                            image.recycle();
                        }
                    }
                } catch (ReflectiveOperationException | InterruptedException error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    @Test public void actualNativeMaskHasSouthernLightingCurvedTerminatorAndFaintEarthshine() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                AtomicLong clock = new AtomicLong(time("2026-03-22T20:00:00Z"));
                ThemeBackgroundView view = attach(activity, clock);
                view.layout(0, 0, 960, 540);
                assertNotNull(field(view, "night"));
                assertEquals(1920, ((Bitmap) field(view, "night")).getWidth());
                assertEquals(512, ((Bitmap) field(view, "moon")).getWidth());
                Bitmap full = draw(view);
                view.setDefaultSelected(false);
                Bitmap bare = draw(view);
                assertTrue("Moon must be centred at the approved x-position and unchanged y-height",
                        distance(full.getPixel(691, 81), bare.getPixel(691, 81)) > 40);
                assertEquals("The old x-position must no longer contain the Moon",
                        0, distance(full.getPixel(624, 81), bare.getPixel(624, 81)), 0);
                view.setDefaultSelected(true);
                view.scene.introMillis = 5000;
                Bitmap crescent = draw(view);
                assertTrue("The terminator must curve, not be a straight wipe",
                        contrast(crescent, bare, full, 680, 95) > .75);
                assertTrue(contrast(crescent, bare, full, 680, 81) < .20);

                clock.set(time("2026-03-25T20:00:00Z"));
                view.updateTime();
                view.scene.introMillis = 5000;
                Bitmap waxing = draw(view);
                assertTrue(contrast(waxing, bare, full, 681, 81) > .85);
                assertTrue(contrast(waxing, bare, full, 701, 81) < .20);
                save(waxing, "lunar-waxing");
                clock.set(time("2026-03-11T20:00:00Z"));
                view.updateTime();
                // A different night has an intro; show its completed phase for the drawing assertion.
                view.scene.introMillis = 5000;
                Bitmap waning = draw(view);
                assertTrue(contrast(waning, bare, full, 681, 81) < .20);
                assertTrue(contrast(waning, bare, full, 701, 81) > .85);
                save(waning, "lunar-waning");
                clock.set(time("2026-03-19T20:00:00Z"));
                view.updateTime();
                view.scene.introMillis = 5000;
                Bitmap newMoon = draw(view);
                for (int x : new int[] {681, 701}) {
                    double earthshine = contrast(newMoon, bare, full, x, 81);
                    assertTrue("New Moon keeps faint earthshine, not a bright disc: " + earthshine,
                            earthshine > .03 && earthshine < .20);
                }
                save(crescent, "lunar-crescent");
                save(newMoon, "lunar-new");
                for (Bitmap image : new Bitmap[] {full, bare, crescent, waxing, waning, newMoon})
                    image.recycle();
            });
        }
    }

    @Test public void nativeSceneUsesSuppliedDayAndNightArtWithNoDaytimeMoonAndNoCustomOverlay() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                AtomicLong clock = new AtomicLong(time("2026-03-22T12:00:00Z"));
                ThemeBackgroundView view = attach(activity, clock);
                view.layout(0, 0, 960, 540);
                Bitmap day = draw(view);
                saveHome(activity, "lunar-day");
                view.scene.introMillis = 5000;
                Bitmap dayWithoutIntro = draw(view);
                assertTrue("No added Moon at any daytime intro progress", day.sameAs(dayWithoutIntro));
                assertNotNull(field(view, "day"));
                assertNull("Only the current wallpaper stays decoded while idle", field(view, "night"));
                clock.set(time("2026-03-22T20:00:00Z"));
                view.updateTime();
                view.scene.advance(2000, true);
                view.setOutputWidth(1920);
                assertEquals(0, view.scene.introMillis);
                Bitmap night = draw(view);
                assertFalse(day.sameAs(night));
                saveHome(activity, "lunar-full-home");
                view.scene.advance(5000, true);
                saveHome(activity, "lunar-phase-home");
                view.setDefaultSelected(false);
                Bitmap customFallback = draw(view);
                assertEquals(0, distance(customFallback.getPixel(691, 81),
                        ((Bitmap) field(view, "night")).getPixel(1383, 163)), 8);
                assertFalse((boolean) field(view, "running"));
                view.setVideoVisible(true);
                assertEquals(View.INVISIBLE, view.getVisibility());
                assertFalse((boolean) field(view, "running"));
                day.recycle();
                dayWithoutIntro.recycle();
                night.recycle();
                customFallback.recycle();
            });
        }
    }

    private void saveHome(AuroraActivity activity, String name) {
        Bitmap image = draw((AuroraHomeView) field(activity, "home"));
        save(image, name);
        image.recycle();
    }

    @Test public void fourKArtRetainsResolutionDuringCrossfadeAndMoonDrawsAtOutputResolution() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                AtomicLong clock = new AtomicLong(time("2026-03-22T12:00:00Z"));
                ThemeBackgroundView view = attach(activity, clock);
                view.layout(0, 0, 960, 540);
                view.setOutputWidth(3840);
                assertEquals(3840, ((Bitmap) field(view, "day")).getWidth());
                clock.set(time("2026-03-22T20:00:00Z"));
                view.updateTime();
                assertEquals(3840, ((Bitmap) field(view, "night")).getWidth());
                view.scene.advance(1000, true);
                assertEquals(.5, view.scene.nightBlend, 1e-6);
                view.scene.advance(1000, true);
                view.setOutputWidth(3840);
                assertNull(field(view, "day"));
                assertEquals(0, view.scene.introMillis);
                Bitmap detail = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(detail);
                canvas.scale(4, 4);
                canvas.translate(64 - ThemeBackgroundView.MOON_X, 64 - ThemeBackgroundView.MOON_Y);
                view.draw(canvas);
                int full = detail.getPixel(296, 256);
                save(detail, "lunar-4k-full-detail");
                view.scene.advance(5000, true);
                view.draw(canvas);
                assertTrue("The actual output-resolution texture must change with the phase",
                        distance(full, detail.getPixel(296, 256)) > 30);
                save(detail, "lunar-4k-phase-detail");
                detail.recycle();
            });
        }
    }

    @Test public void nativeHoldAndMorphPauseAcrossInactivityCustomVideoAndDetachment() throws Exception {
        AtomicReference<ThemeBackgroundView> owner = new AtomicReference<>();
        long[] paused = new long[1];
        CountDownLatch held = new CountDownLatch(1), morphed = new CountDownLatch(1);
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                ThemeBackgroundView view = attach(activity,
                        new AtomicLong(time("2026-03-22T20:00:00Z")));
                owner.set(view);
                view.setActive(true);
                view.postDelayed(() -> {
                    view.setActive(false);
                    paused[0] = view.scene.introMillis;
                    held.countDown();
                }, 1100);
            });
            assertTrue("The native UI must reach its scheduled pause", held.await(10, TimeUnit.SECONDS));
            scene.onActivity(activity -> {
                assertTrue("Visible hold elapsed: " + paused[0], paused[0] >= 1000 && paused[0] < 2500);
            });
            SystemClock.sleep(650);
            scene.onActivity(activity -> {
                assertEquals(paused[0], owner.get().scene.introMillis);
                owner.get().setActive(true);
                assertEquals(paused[0], owner.get().scene.introMillis);
                owner.get().postDelayed(() -> {
                    owner.get().setDefaultSelected(false);
                    paused[0] = owner.get().scene.introMillis;
                    morphed.countDown();
                }, 2100);
            });
            assertTrue("The native morph must advance", morphed.await(10, TimeUnit.SECONDS));
            scene.onActivity(activity -> {
                assertTrue("Only visible time counts towards the morph", paused[0] > 3000 && paused[0] < 4800);
            });
            SystemClock.sleep(650);
            scene.onActivity(activity -> {
                assertEquals(paused[0], owner.get().scene.introMillis);
                owner.get().setDefaultSelected(true);
                assertEquals(paused[0], owner.get().scene.introMillis);
            });
            SystemClock.sleep(300);
            scene.onActivity(activity -> {
                FrameLayout root = activity.findViewById(R.id.home_root);
                root.removeView(owner.get());
                paused[0] = owner.get().scene.introMillis;
                assertFalse((boolean) field(owner.get(), "running"));
            });
            SystemClock.sleep(300);
            scene.onActivity(activity -> {
                assertEquals(paused[0], owner.get().scene.introMillis);
                Bundle saved = new Bundle();
                owner.get().save(saved);
                ThemeBackgroundView restored = attach(activity,
                        new AtomicLong(time("2026-03-22T20:00:00Z")));
                restored.restore(saved);
                assertEquals(paused[0], restored.scene.introMillis);
                restored.setActive(true);
                assertEquals(paused[0], restored.scene.introMillis);
            });
        }
    }

    @Test public void realHomeForwardsPanelsCustomSelectionAndActivitySavedState() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                ThemeBackgroundView view = activity.findViewById(R.id.background_still);
                assertTrue((boolean) field(view, "active"));
                view.scene.introMillis = 4000;
            });
            scene.recreate();
            scene.onActivity(activity -> {
                ThemeBackgroundView view = activity.findViewById(R.id.background_still);
                assertTrue("Activity recreation must not replay the full-Moon intro", view.scene.introMillis >= 4000);
            });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_MENU));
            scene.onActivity(activity -> {
                ThemeBackgroundView view = activity.findViewById(R.id.background_still);
                assertFalse((boolean) field(view, "active"));
                assertFalse((boolean) field(view, "running"));
            });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.pressKey(KeyEvent.KEYCODE_BACK));
            scene.onActivity(activity -> {
                AuroraHomeView home = (AuroraHomeView) field(activity, "home");
                ThemeBackgroundView view = activity.findViewById(R.id.background_still);
                assertTrue((boolean) field(view, "active"));
                home.setDefaultBackground(false);
                assertFalse((boolean) field(view, "running"));
                home.setVideoVisible(true);
                assertEquals(View.INVISIBLE, view.getVisibility());
            });
        }
    }

    private void scale(float value) throws Exception {
        String command = "settings put global animator_duration_scale " + value;
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            while (input.read(new byte[256]) != -1) { }
        }
        for (int i = 0; i < 100 && ValueAnimator.areAnimatorsEnabled() != (value > 0); i++)
            SystemClock.sleep(20);
        assertEquals(value > 0, ValueAnimator.areAnimatorsEnabled());
    }

    @Test public void reducedMotionInstantlySwitchesArtButRetainsTheActualThreeSecondHold() throws Exception {
        float previous = Settings.Global.getFloat(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        AtomicReference<ThemeBackgroundView> owner = new AtomicReference<>();
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            try {
                scale(0);
                scene.onActivity(activity -> {
                    AtomicLong clock = new AtomicLong(time("2026-03-22T12:00:00Z"));
                    ThemeBackgroundView view = attach(activity, clock);
                    owner.set(view);
                    view.setActive(true);
                    clock.set(time("2026-03-22T20:00:00Z"));
                    view.updateTime();
                    assertEquals(1, view.scene.nightBlend, 0);
                    assertEquals(1, view.scene.illumination(), 0);
                    assertEquals(3000, view.scene.nextDelay());
                });
                SystemClock.sleep(2400);
                scene.onActivity(activity -> assertEquals(1, owner.get().scene.illumination(), 0));
                SystemClock.sleep(800);
                scene.onActivity(activity -> {
                    assertEquals(owner.get().scene.phase.fraction, owner.get().scene.illumination(), 1e-9);
                    assertFalse(owner.get().scene.pending());
                });
            } finally { scale(previous); }
        }
    }

    @Test public void nightBlendListenerTracksSceneBlendSelectionVideoPauseReducedMotionAndRestore()
            throws Exception {
        float previous = Settings.Global.getFloat(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            try {
                AtomicLong clock = new AtomicLong(time("2026-03-22T12:00:00Z"));
                final ThemeBackgroundView[] owner = new ThemeBackgroundView[1];
                final double[] latest = new double[] {Double.NaN};
                final int[] updates = new int[1];
                final double[] beforeRestore = new double[1];
                final long[] pausedIntro = new long[1];
                scene.onActivity(activity -> {
                    ThemeBackgroundView view = attach(activity, clock);
                    owner[0] = view;
                    view.layout(0, 0, 960, 540);
                    view.setNightBlendListener(value -> {
                        latest[0] = value;
                        updates[0]++;
                    });
                    assertEquals(0, latest[0], 0);
                    clock.set(time("2026-03-22T20:00:00Z"));
                    view.updateTime();
                    view.scene.advance(1000, true);
                    view.updateTime();
                    assertEquals(.5, latest[0], 1e-9);
                    view.setDefaultSelected(false);
                    assertEquals(0, latest[0], 0);
                    view.setDefaultSelected(true);
                    assertEquals(.5, latest[0], 1e-9);
                    view.setVideoVisible(true);
                    assertEquals(0, latest[0], 0);
                    view.setVideoVisible(false);
                    assertEquals(.5, latest[0], 1e-9);
                    view.setActive(true);
                    view.setActive(false);
                    assertFalse((boolean) field(view, "running"));
                    pausedIntro[0] = view.scene.introMillis;
                    Bundle state = new Bundle();
                    view.save(state);
                    beforeRestore[0] = view.scene.nightBlend;
                    ThemeBackgroundView restored = attach(activity, clock);
                    restored.layout(0, 0, 960, 540);
                    restored.setNightBlendListener(value -> {
                        latest[0] = value;
                        updates[0]++;
                    });
                    restored.restore(state);
                    owner[0] = restored;
                    assertEquals(beforeRestore[0], restored.scene.nightBlend, 1e-9);
                    assertEquals(beforeRestore[0], latest[0], 1e-9);
                });
                SystemClock.sleep(300);
                scene.onActivity(activity -> {
                    assertEquals(beforeRestore[0], latest[0], 0);
                    assertEquals(pausedIntro[0], owner[0].scene.introMillis);
                    int count = updates[0];
                    owner[0].updateTime();
                    assertEquals("An unchanged blend must not be republished", count, updates[0]);
                });
                scale(0);
                scene.onActivity(activity -> {
                    owner[0].setDefaultSelected(true);
                    clock.set(time("2026-03-22T12:00:00Z"));
                    owner[0].updateTime();
                    assertEquals(0, latest[0], 0);
                    clock.set(time("2026-03-22T20:00:00Z"));
                    owner[0].updateTime();
                    assertEquals(1, latest[0], 0);
                    assertTrue(updates[0] > 2);
                });
            } finally { scale(previous); }
        }
    }
}
