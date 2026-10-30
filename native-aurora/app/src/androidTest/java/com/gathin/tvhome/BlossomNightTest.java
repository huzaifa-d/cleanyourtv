package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.view.View;
import android.view.View.MeasureSpec;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Lifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BlossomNightTest {
    private static final int SIZE = BlossomTile.SIZE;
    private static final int PLUM_R = 24, PLUM_G = 7, PLUM_B = 24;
    private static final float SHADE_RADIUS = 37f, SHADE_MAX = .33f;

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

    private static void measureAndLayout(BlossomTile tile) {
        int exact = MeasureSpec.makeMeasureSpec(SIZE, MeasureSpec.EXACTLY);
        tile.measure(exact, exact);
        tile.layout(0, 0, SIZE, SIZE);
        assertEquals(SIZE, tile.getWidth());
        assertEquals(SIZE, tile.getHeight());
        assertEquals(SIZE, tile.flower.getWidth());
        assertEquals(SIZE, tile.flower.getHeight());
    }

    private static Bitmap syntheticFlower(boolean withHole, int colour) {
        Bitmap flower = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(flower);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        canvas.drawRect(0, 0, SIZE, SIZE, paint);
        if (withHole) {
            Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawCircle(SIZE / 2f, SIZE / 2f, 11f, clear);
        }
        return flower;
    }

    private static Bitmap blossomArtwork() {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getAssets().open("aurora/cherry/blossom.png")) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            assertNotNull(bitmap);
            return bitmap;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private static float relativeAlpha(float radius) {
        float t = Math.max(0f, Math.min(1f, radius / SHADE_RADIUS));
        if (t <= .4f) return 1f + (t / .4f) * (.84f - 1f);
        if (t <= .75f) return .84f + ((t - .4f) / .35f) * (.32f - .84f);
        return .32f + ((t - .75f) / .25f) * (0f - .32f);
    }

    private static int expectedChannel(int base, int plum, float radius, float blend) {
        float alpha = SHADE_MAX * blend * relativeAlpha(radius);
        return Math.round(base * (1f - alpha) + plum * alpha);
    }

    private static void assertColourNear(int expected, int actual) {
        assertEquals(Color.red(expected), Color.red(actual), 2);
        assertEquals(Color.green(expected), Color.green(actual), 2);
        assertEquals(Color.blue(expected), Color.blue(actual), 2);
    }

    @Test public void measuredFixtureMatchesApprovedShadeStrengthAndFeatherStops() {
        int flower = 0xFFEFD9EF;
        BlossomTile tile = new BlossomTile(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                BrandShortcut.NETFLIX, syntheticFlower(false, flower), 0f, () -> { });
        measureAndLayout(tile);
        tile.mark.setVisibility(View.INVISIBLE);
        tile.setNightBlend(0);
        Bitmap day = draw(tile);
        assertEquals(255, Color.alpha(day.getPixel(59, 59)));
        assertTrue(Color.red(day.getPixel(59, 59)) > PLUM_R);
        tile.setNightBlend(1);
        Bitmap full = draw(tile);
        tile.setNightBlend(.5);
        Bitmap half = draw(tile);
        for (int radius : new int[] {0, 15, 28, 35}) {
            int x = 59 + radius, y = 59;
            int base = day.getPixel(x, y);
            assertEquals("Fixture source must remain fully opaque", 255, Color.alpha(base));
            int fullExpected = Color.argb(255, expectedChannel(Color.red(base), PLUM_R, radius, 1f),
                    expectedChannel(Color.green(base), PLUM_G, radius, 1f),
                    expectedChannel(Color.blue(base), PLUM_B, radius, 1f));
            int halfExpected = Color.argb(255, expectedChannel(Color.red(base), PLUM_R, radius, .5f),
                    expectedChannel(Color.green(base), PLUM_G, radius, .5f),
                    expectedChannel(Color.blue(base), PLUM_B, radius, .5f));
            assertColourNear(fullExpected, full.getPixel(x, y));
            assertColourNear(halfExpected, half.getPixel(x, y));
        }
        assertEquals("Outside 37px radius shading must be zero", day.getPixel(10, 10), full.getPixel(10, 10));
        day.recycle();
        full.recycle();
        half.recycle();
    }

    @Test public void measuredFixturePreservesTransparentHolesAndOuterPixels() {
        BlossomTile tile = new BlossomTile(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                BrandShortcut.NETFLIX, syntheticFlower(true, 0xFFEFD9EF), 0f, () -> { });
        measureAndLayout(tile);
        tile.mark.setVisibility(View.INVISIBLE);
        tile.setNightBlend(0);
        Bitmap day = draw(tile);
        tile.setNightBlend(1);
        Bitmap night = draw(tile);
        assertEquals(0, Color.alpha(day.getPixel(59, 59)));
        assertEquals(0, day.getPixel(59, 59));
        assertEquals(0, Color.alpha(night.getPixel(59, 59)));
        assertEquals(0, night.getPixel(59, 59));
        assertEquals(day.getPixel(10, 10), night.getPixel(10, 10));
        day.recycle();
        night.recycle();
    }

    @Test public void logoRemainsOpaqueWhiteAboveShadeWithMeasuredFixture() {
        BlossomTile tile = new BlossomTile(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                BrandShortcut.NETFLIX, syntheticFlower(false, 0xFFEFD9EF), 0f, () -> { });
        measureAndLayout(tile);
        Bitmap logo = Bitmap.createBitmap(tile.mark.getLayoutParams().width, tile.mark.getLayoutParams().height,
                Bitmap.Config.ARGB_8888);
        logo.eraseColor(Color.WHITE);
        tile.setLogos(logo, logo);
        tile.mark.setVisibility(View.VISIBLE);
        tile.setNightBlend(0);
        Bitmap day = draw(tile);
        int logoPixel = day.getPixel(59, 59);
        assertEquals(255, Color.alpha(logoPixel));
        assertEquals(255, Color.red(logoPixel));
        assertEquals(255, Color.green(logoPixel));
        assertEquals(255, Color.blue(logoPixel));
        tile.setNightBlend(1);
        Bitmap night = draw(tile);
        assertEquals(logoPixel, night.getPixel(59, 59));
        day.recycle();
        night.recycle();
        logo.recycle();
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test public void hardwarePathPreservesArtworkAlphaAndOuterPixelsAcrossAllAngles() throws InterruptedException {
        Bitmap blossom = blossomArtwork();
        int[] angles = {-28, 46, -84, 118, -142, 76, 164};
        for (int scale : new int[] {1, 4}) {
            for (int angle : angles) {
                BlossomTile tile = new BlossomTile(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        BrandShortcut.NETFLIX, blossom, angle, () -> { });
                measureAndLayout(tile);
                tile.mark.setVisibility(View.INVISIBLE);
                tile.setNightBlend(0);
                Bitmap day = ThemeBackgroundTest.hardwareRegion(tile, scale, 0, 0, SIZE);
                tile.setNightBlend(1);
                Bitmap night = ThemeBackgroundTest.hardwareRegion(tile, scale, 0, 0, SIZE);
                int cx = 59 * scale, cy = 59 * scale;
                int darkened = 0, transparent = 0, outside = 0, strongAlpha = 0, outerPetals = 0;
                for (int y = 0; y < day.getHeight(); y += Math.max(1, scale)) {
                    for (int x = 0; x < day.getWidth(); x += Math.max(1, scale)) {
                        int d = day.getPixel(x, y), n = night.getPixel(x, y);
                        int alphaDay = Color.alpha(d), alphaNight = Color.alpha(n);
                        assertTrue("Night center shade must preserve source alpha",
                                Math.abs(alphaDay - alphaNight) <= 1);
                        if (alphaDay >= 8) strongAlpha++;
                        double r = Math.hypot(x - cx, y - cy) / scale;
                        if (r > 59.5) {
                            outside++;
                            assertEquals("No shading outside real blossom coverage", d, n);
                        }
                        if (r > 38 && alphaDay >= 248) {
                            outerPetals++;
                            // The isolated GPU layer adds one premultiplied-channel rounding step.
                            assertColourNear(d, n);
                        }
                        if (alphaDay == 0) {
                            transparent++;
                            assertEquals("Transparent flower edges must stay transparent", 0, alphaNight);
                            continue;
                        }
                        if (r < 20 && Color.red(n) < Color.red(d)) darkened++;
                    }
                }
                assertTrue("Fixture must include transparent edge pixels", transparent > 50);
                assertTrue("Fixture must include outside-of-blossom pixels", outside > 50);
                assertTrue("Fixture must include solid blossom pixels", strongAlpha > 50);
                assertTrue("Fixture must include visible outer petals", outerPetals > 50);
                assertTrue("Inner blossom pixels must darken", darkened > 20);
                day.recycle();
                night.recycle();
            }
        }
        blossom.recycle();
    }

    @Test public void recreationRestoresBlendAndKeepsDefaultCustomAndVideoGating() {
        final float[] beforeBlend = new float[1];
        final int[] beforeDefault = new int[1], beforeCustom = new int[1], beforeVideo = new int[1];
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.moveToState(Lifecycle.State.CREATED);
            scene.onActivity(activity -> {
                AuroraHomeView home = (AuroraHomeView) field(activity, "home");
                ThemeBackgroundView still = activity.findViewById(R.id.background_still);
                BlossomTile tile = activity.findViewById(R.id.netflix);
                tile.flower.setImageBitmap(syntheticFlower(false, 0xFFEFD9EF));
                tile.flower.setRotation(0f);
                tile.mark.setVisibility(View.INVISIBLE);
                measureAndLayout(tile);
                long now = System.currentTimeMillis();
                ZoneId zone = ZoneId.systemDefault();
                ZonedDateTime local = Instant.ofEpochMilli(now).atZone(zone);
                ThemeScene transition = new ThemeScene();
                transition.update(local.withHour(still.scene.night ? 12 : 20)
                        .toInstant().toEpochMilli(), zone, true);
                transition.update(now, zone, true);
                transition.advance(1000, true);
                assertEquals(.5, transition.nightBlend, 0);
                Bundle snapshot = new Bundle();
                snapshot.putDoubleArray("theme_scene", transition.save());
                still.restore(snapshot);
                beforeBlend[0] = (float) still.scene.nightBlend;
                assertEquals(.5f, beforeBlend[0], 0);
                home.setVideoVisible(false);
                home.setDefaultBackground(true);
                beforeDefault[0] = draw(tile).getPixel(59, 59);
                home.setDefaultBackground(false);
                beforeCustom[0] = draw(tile).getPixel(59, 59);
                home.setDefaultBackground(true);
                home.setVideoVisible(true);
                beforeVideo[0] = draw(tile).getPixel(59, 59);
                assertEquals(beforeCustom[0], beforeVideo[0]);
                assertTrue(Color.red(beforeDefault[0]) < Color.red(beforeCustom[0]));
                Bundle saved = new Bundle();
                home.saveState(saved);
                assertNotNull(saved.getDoubleArray("theme_scene"));
                new AuroraPreferences(activity.getSharedPreferences("aurora", 0))
                        .setVideo("restoration-test-missing.mp4");
                home.setDefaultBackground(false);
                home.setVideoVisible(false);
            });
            scene.recreate();
            scene.onActivity(activity -> {
                AuroraHomeView home = (AuroraHomeView) field(activity, "home");
                ThemeBackgroundView still = activity.findViewById(R.id.background_still);
                BlossomTile tile = activity.findViewById(R.id.netflix);
                tile.flower.setImageBitmap(syntheticFlower(false, 0xFFEFD9EF));
                tile.flower.setRotation(0f);
                tile.mark.setVisibility(View.INVISIBLE);
                measureAndLayout(tile);
                assertEquals(beforeBlend[0], (float) still.scene.nightBlend, .0001f);
                assertEquals("Selected missing video must restore unshaded fallback tiles",
                        beforeCustom[0], draw(tile).getPixel(59, 59));
                home.setVideoVisible(false);
                home.setDefaultBackground(true);
                int restoredDefault = draw(tile).getPixel(59, 59);
                home.setDefaultBackground(false);
                int restoredCustom = draw(tile).getPixel(59, 59);
                home.setDefaultBackground(true);
                home.setVideoVisible(true);
                int restoredVideo = draw(tile).getPixel(59, 59);
                assertEquals(beforeDefault[0], restoredDefault);
                assertEquals(beforeCustom[0], restoredCustom);
                assertEquals(beforeVideo[0], restoredVideo);
                assertEquals(restoredCustom, restoredVideo);
                assertTrue(Color.red(restoredDefault) < Color.red(restoredCustom));
            });
        }
    }
}
