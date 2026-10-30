package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class RainBackgroundTest {
    private Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private ThemeBackgroundView attach(AuroraActivity activity) {
        ((AuroraHomeView) field(activity, "home")).setHomeActive(false);
        ThemeBackgroundView view = new ThemeBackgroundView(activity,
                () -> Instant.parse("2026-03-22T20:00:00Z").toEpochMilli(), () -> ZoneId.of("UTC"));
        FrameLayout root = activity.findViewById(R.id.home_root);
        root.removeView(activity.findViewById(R.id.background_still));
        root.addView(view, 0, new FrameLayout.LayoutParams(960, 540));
        view.layout(0, 0, 960, 540);
        view.setOutputWidth(1920);
        view.scene.introMillis = 5000;
        return view;
    }

    private Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private int largestChannelDifference(Bitmap first, Bitmap second) {
        int largest = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            int a = first.getPixel(x, y), b = second.getPixel(x, y);
            for (int shift = 0; shift < 32; shift += 8)
                largest = Math.max(largest, Math.abs((a >> shift & 255) - (b >> shift & 255)));
        }
        return largest;
    }

    private void saveFixture(AuroraActivity activity, String name, Bitmap bitmap) throws java.io.IOException {
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                new java.io.File(activity.getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    @Test @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    public void actualArtworkOccludesPetalsOverOpaqueForegroundOnTheGpu() {
        try (ActivityScenario<AuroraActivity> scenario = ActivityScenario.launch(AuroraActivity.class)) {
            scenario.onActivity(activity -> {
                ThemeBackgroundView view = attach(activity);
                PetalView petals = new PetalView(activity);
                petals.layout(0, 0, 960, 540);
                view.setPetals(petals);
                PetalMotion[] motion = (PetalMotion[]) field(petals, "petals");
                Bitmap[] textures = (Bitmap[]) field(petals, "textures");
                for (int[] fixture : new int[][] {{10, 361, 510}, {20, 361, 510}}) {
                    int x = fixture[1], y = fixture[2];
                    view.scene.update(Instant.parse("2026-03-22T" + fixture[0] + ":00:00Z").toEpochMilli(),
                            ZoneId.of("UTC"), true);
                    view.scene.advance(7000, true);
                    view.setOutputWidth(960);
                    double phase = (y / 540.0 + .06) / 1.12;
                    float startX = (float) (x / 960.0 - .015 * Math.sin(phase * 3 * Math.PI));
                    for (int i = 0; i < motion.length; i++)
                        motion[i] = new PetalMotion(0, .018f, 16000, phase, startX, 0,
                                i == 0 ? 1 : 0, 0);
                    Bitmap texture = textures[0];
                    try {
                        Field foregroundField = ThemeBackgroundView.class.getDeclaredField(
                                fixture[0] == 10 ? "dayForeground" : "nightForeground");
                        foregroundField.setAccessible(true);
                        Bitmap foreground = (Bitmap) foregroundField.get(view);
                        for (int py = y - 14; py < y + 14; py++)
                            for (int px = x - 14; px < x + 14; px++)
                                assertEquals("Real artwork must cover the entire near flower fixture",
                                        255, Color.alpha(foreground.getPixel(px, py)));
                        Bitmap withPetal = ThemeBackgroundTest.hardwareRegion(view, 1, x - 14, y - 14, 28);
                        textures[0] = null;
                        Bitmap withoutPetal = ThemeBackgroundTest.hardwareRegion(view, 1, x - 14, y - 14, 28);
                        // The emulator GPU can round opaque filtered RGB by one 8-bit step.
                        assertTrue("Real foliage must hide the petal, not just reduce its contrast",
                                largestChannelDifference(withPetal, withoutPetal) <= 1);
                        try {
                            foregroundField.set(view, null);
                            Bitmap openWithoutPetal = ThemeBackgroundTest.hardwareRegion(
                                    view, 1, x - 14, y - 14, 28);
                            textures[0] = texture;
                            Bitmap openWithPetal = ThemeBackgroundTest.hardwareRegion(
                                    view, 1, x - 14, y - 14, 28);
                            assertTrue("The GPU fixture must expose a genuine petal without the foreground",
                                    largestChannelDifference(openWithPetal, openWithoutPetal) > 8);
                            String scene = fixture[0] == 10 ? "morning" : "night";
                            saveFixture(activity, "real-foreground-" + scene + "-before", openWithPetal);
                            saveFixture(activity, "real-foreground-" + scene + "-after", withPetal);
                            openWithPetal.recycle();
                            openWithoutPetal.recycle();
                        } finally {
                            foregroundField.set(view, foreground);
                        }
                        withPetal.recycle();
                        withoutPetal.recycle();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    } catch (ReflectiveOperationException | java.io.IOException error) {
                        throw new AssertionError(error);
                    } finally { textures[0] = texture; }
                }
            });
        }
    }

    @Test public void actualHomeHidesFallingPetalsBehindTheForeground() {
        try (ActivityScenario<AuroraActivity> scenario = ActivityScenario.launch(AuroraActivity.class)) {
            scenario.onActivity(activity -> {
                ((AuroraHomeView) field(activity, "home")).setHomeActive(false);
                ThemeBackgroundView background = activity.findViewById(R.id.background_still);
                PetalView petals = activity.findViewById(R.id.petals);
                Bitmap[] textures = (Bitmap[]) field(petals, "textures");
                Bitmap[] originalTextures = textures.clone();
                Bitmap cover = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
                cover.eraseColor(0xff284050);
                try {
                    Field day = ThemeBackgroundView.class.getDeclaredField("dayForeground");
                    Field night = ThemeBackgroundView.class.getDeclaredField("nightForeground");
                    day.setAccessible(true);
                    night.setAccessible(true);
                    Object originalDay = day.get(background), originalNight = night.get(background);
                    try {
                        day.set(background, cover);
                        night.set(background, cover);
                        Bitmap petalInk = draw(petals);
                        int[] pixels = new int[960 * 540];
                        petalInk.getPixels(pixels, 0, 960, 0, 0, 960, 540);
                        assertTrue("Fixture must contain actual visible petals",
                                Arrays.stream(pixels).anyMatch(pixel -> Color.alpha(pixel) > 0));
                        Bitmap withPetals = draw(activity.findViewById(R.id.home_root));
                        Arrays.fill(textures, null);
                        Bitmap withoutPetals = draw(activity.findViewById(R.id.home_root));
                        assertEquals("Fixture must actually cover the backdrop",
                                0xff284050, withoutPetals.getPixel(0, 300));
                        assertTrue("Foreground foliage must hide falling petals in the actual Home composition",
                                withPetals.sameAs(withoutPetals));
                        cover.eraseColor(Color.TRANSPARENT);
                        Bitmap uncoveredWithoutPetals = draw(activity.findViewById(R.id.home_root));
                        System.arraycopy(originalTextures, 0, textures, 0, textures.length);
                        Bitmap uncoveredWithPetals = draw(activity.findViewById(R.id.home_root));
                        assertFalse("Petals must remain visible through transparent foreground",
                                uncoveredWithPetals.sameAs(uncoveredWithoutPetals));
                        petalInk.recycle();
                        withPetals.recycle();
                        withoutPetals.recycle();
                        uncoveredWithPetals.recycle();
                        uncoveredWithoutPetals.recycle();
                    } finally {
                        day.set(background, originalDay);
                        night.set(background, originalNight);
                    }
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                finally {
                    System.arraycopy(originalTextures, 0, textures, 0, textures.length);
                    cover.recycle();
                }
            });
        }
    }

    @Test public void rainMovesBehindOpaqueForegroundAndReplaysTheExactLoop() {
        try (ActivityScenario<AuroraActivity> scenario = ActivityScenario.launch(AuroraActivity.class)) {
            scenario.onActivity(activity -> {
                ThemeBackgroundView view = attach(activity);
                Bitmap dry = draw(view);
                view.setRain(true);
                Bitmap wet = draw(view);
                Bitmap foreground = (Bitmap) field(view, "nightForeground");
                assertNotNull(foreground);
                int changed = 0, covered = 0;
                for (int y = 10; y < 530; y += 2) for (int x = 10; x < 950; x += 2) {
                    if (dry.getPixel(x, y) != wet.getPixel(x, y)) changed++;
                    if (Color.alpha(foreground.getPixel(x * 2, y * 2)) == 255
                            && Color.alpha(foreground.getPixel(x * 2 + 1, y * 2)) == 255
                            && Color.alpha(foreground.getPixel(x * 2, y * 2 + 1)) == 255
                            && Color.alpha(foreground.getPixel(x * 2 + 1, y * 2 + 1)) == 255) {
                        assertEquals("Rain must be occluded by near-camera foliage",
                                dry.getPixel(x, y), wet.getPixel(x, y));
                        covered++;
                    }
                }
                assertTrue("Rain must actually change visible pixels", changed > 100);
                assertTrue("Fixture must sample genuine opaque foreground", covered > 1000);
                Bundle state = new Bundle();
                view.save(state);
                state.putLong("rain_millis", 12000);
                view.restore(state);
                Bitmap loop = draw(view);
                assertTrue("The full scene must loop exactly", wet.sameAs(loop));
                state.putLong("rain_millis", 800);
                view.restore(state);
                Bitmap moved = draw(view);
                assertFalse("Rain cannot be a static overlay", wet.sameAs(moved));
                view.setDefaultSelected(false);
                Bitmap fallback = draw(view);
                view.setRain(false);
                Bitmap withoutRain = draw(view);
                assertTrue("Custom-video fallback must not run rain", fallback.sameAs(withoutRain));
                for (Bitmap image : new Bitmap[] {dry, wet, loop, moved, fallback, withoutRain}) image.recycle();
            });
        }
    }

    @Test public void visibleRainTimePausesAndRestoresWithoutAJump() {
        AtomicReference<ThemeBackgroundView> target = new AtomicReference<>();
        try (ActivityScenario<AuroraActivity> scenario = ActivityScenario.launch(AuroraActivity.class)) {
            scenario.onActivity(activity -> {
                ThemeBackgroundView view = attach(activity);
                view.setRain(true);
                view.setActive(true);
                target.set(view);
            });
            SystemClock.sleep(160);
            Bundle saved = new Bundle();
            scenario.onActivity(activity -> {
                target.get().setActive(false);
                target.get().save(saved);
                assertTrue(saved.getLong("rain_millis") > 0);
            });
            SystemClock.sleep(250);
            scenario.onActivity(activity -> {
                Bundle paused = new Bundle();
                target.get().save(paused);
                assertEquals(saved.getLong("rain_millis"), paused.getLong("rain_millis"));
                ThemeBackgroundView recreated = attach(activity);
                recreated.restore(saved);
                Bundle restored = new Bundle();
                recreated.save(restored);
                assertEquals(saved.getLong("rain_millis"), restored.getLong("rain_millis"));
                assertFalse((boolean) field(target.get(), "running"));
            });
        }
    }
}
