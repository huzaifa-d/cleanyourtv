package com.gathin.tvhome;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import android.content.Context;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.IntentFilter;
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
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CherryDrawerTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Before public void reset() {
        context.getSharedPreferences("aurora", 0).edit().clear()
                .putString("video", "drawer-test-missing.mp4").commit();
    }
    @After public void cleanup() { context.getSharedPreferences("aurora", 0).edit().clear().commit(); }
    private void menu() { onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_MENU)); }

    @Test public void approvedWineDrawerAndPaleFocusTileAreRendered() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withTagValue(is("cherry_drawer"))).check((view, error) -> {
                if (error != null) throw error;
                Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    view.getBackground().draw(new Canvas(image));
                    int centre = image.getPixel(image.getWidth() / 2, image.getHeight() / 2);
                    assertEquals(70, Color.red(centre), 3);
                    assertEquals(19, Color.green(centre), 3);
                    assertEquals(35, Color.blue(centre), 3);
                } finally { image.recycle(); }
            });
            onView(withText(R.string.background)).check((view, error) -> {
                if (error != null) throw error;
                Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    view.getBackground().draw(new Canvas(image));
                    assertEquals(0xfff5dce7, image.getPixel(image.getWidth() / 2, image.getHeight() / 2));
                    assertEquals(0xff3b1118, ((android.widget.TextView) view).getCurrentTextColor());
                } finally { image.recycle(); }
            });
        }
    }

    @Test public void approvedDrawerHasRightAlignmentAndIconOnlyHeaderWithoutDedication() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withTagValue(is("cherry_drawer"))).check((view, error) -> {
                if (error != null) throw error;
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                assertTrue(location[0] > context.getResources().getDisplayMetrics().widthPixels / 2);
            });
            onView(withText(R.string.created_with_love)).check(doesNotExist());
            onView(withContentDescription("Switch Google TV profile")).check(matches(isDisplayed()));
            onView(withContentDescription(context.getString(R.string.system_settings)))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.system_settings)).check(doesNotExist());
            for (String title : new String[] {"Screensaver", "Bluetooth", "Accessibility"})
                onView(withText(title)).check(matches(isDisplayed()));
            onView(withText(R.string.background)).check(matches(hasFocus()));
            for (int title : new int[] {R.string.background, R.string.drawer_ram,
                    R.string.screensaver, R.string.inputs, R.string.bluetooth,
                    R.string.accessibility, R.string.app_management,
                    R.string.home_button_setup, R.string.google_home, R.string.daily_standby}) {
                onView(withText(title)).check((view, error) -> {
                    if (error != null) throw error;
                    android.graphics.drawable.Drawable glyph = ((android.widget.TextView) view)
                            .getCompoundDrawablesRelative()[0];
                    assertEquals("A centred glyph must not land on half pixels: tile "
                                    + view.getHeight() + ", glyph " + glyph.getBounds().height(),
                            0, (view.getHeight() - glyph.getBounds().height()) % 2);
                });
            }
        }
    }

    @Test public void dpadReachesHeaderAndStandbyTileAndNestedBackRestoresFocus() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withText(R.string.background)).perform(pressKey(KeyEvent.KEYCODE_DPAD_UP));
            onView(withContentDescription(context.getString(R.string.system_settings)))
                    .check(matches(hasFocus())).perform(pressKey(KeyEvent.KEYCODE_DPAD_RIGHT));
            onView(withContentDescription("Switch Google TV profile"))
                    .check(matches(hasFocus())).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN));
            onView(withId(R.id.ram_switch)).check(matches(hasFocus()))
                    .perform(pressKey(KeyEvent.KEYCODE_DPAD_LEFT));
            onView(withText(R.string.background)).perform(click());
            pressBack();
            onView(withText(R.string.background)).check(matches(hasFocus()));
            for (int i = 0; i < 4; i++)
                onView(withTagValue(is("cherry_drawer"))).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN));
            onView(withText(R.string.google_home)).check(matches(hasFocus()))
                    .perform(pressKey(KeyEvent.KEYCODE_DPAD_RIGHT));
            onView(withText(R.string.daily_standby)).check(matches(hasFocus()));
            pressBack();
            scene.onActivity(activity -> assertTrue(activity.findViewById(R.id.profile).hasFocus()));
        }
    }

    @Test public void nativeShortcutsUseExportedHandlersAndMissingActionsStayInCherry() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                for (String action : new String[] {Settings.ACTION_BLUETOOTH_SETTINGS,
                        Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_ACCESSIBILITY_SETTINGS}) {
                    Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                            new IntentFilter(action), new Instrumentation.ActivityResult(
                                    Activity.RESULT_CANCELED, null), true);
                    try {
                        boolean available = SystemSettings.intent(activity, action) != null;
                        assertEquals(available, SystemSettings.openAction(activity, action));
                        assertEquals(available ? 1 : 0, monitor.getHits());
                    } finally { instrumentation.removeMonitor(monitor); }
                }
                assertFalse(SystemSettings.openAction(activity, "com.huzaifa.aurora.NO_HANDLER"));
            });
        }
    }

    @Test public void compactControlsReplaceAboutWithGoogleHome() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withId(R.id.ram_switch)).check((view, error) -> {
                if (error != null) throw error;
                android.widget.Switch toggle = (android.widget.Switch) view;
                android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                float scale = Math.min(metrics.widthPixels / 960f, metrics.heightPixels / 540f);
                int occupiedWidth = toggle.getCompoundPaddingRight() - toggle.getPaddingRight()
                        - toggle.getSwitchPadding();
                assertEquals("The pill must be shortened horizontally",
                        Math.round(20 * scale), occupiedWidth);
                assertEquals("Shortening the pill must not make it flatter",
                        Math.round(14 * scale), toggle.getTrackDrawable().getBounds().height());
                android.graphics.Rect thumb = inkBounds(toggle.getThumbDrawable());
                assertTrue("Actual thumb must remain a compact circle, not stretch to track height",
                        thumb.width() > 0 && thumb.width() == thumb.height()
                                && thumb.height() <= Math.round(10 * scale));
            });
            onView(withText(R.string.inputs)).check((view, error) -> {
                if (error != null) throw error;
                android.graphics.drawable.Drawable icon = ((android.widget.TextView) view)
                        .getCompoundDrawablesRelative()[0];
                int size = icon.getBounds().width();
                android.graphics.Rect ink = inkBounds(icon);
                assertTrue("HDMI connector must be narrower than the old input-arrow icon",
                        ink.width() > 0 && ink.width() <= Math.round(size * 16f / 24));
            });
            onView(withText(R.string.about)).check(doesNotExist());
            onView(withText(R.string.google_home)).check(matches(isDisplayed()));
        }
    }

    private android.graphics.Rect inkBounds(android.graphics.drawable.Drawable drawable) {
        android.graphics.Rect bounds = drawable.getBounds();
        Bitmap image = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(image);
            canvas.translate(-bounds.left, -bounds.top);
            drawable.draw(canvas);
            int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if (Color.alpha(image.getPixel(x, y)) > 32) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
            return new android.graphics.Rect(left, top, right + 1, bottom + 1);
        } finally { image.recycle(); }
    }

    @Test public void googleHomeLaunchesOnlyItsPanelOrLeavesDrawerUsable() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                "com.google.android.libraries.tv.smarthome.core.SmartHomeMainActivity",
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            onView(withText(R.string.google_home)).perform(click());
            if (monitor.getHits() == 0) {
                onView(withTagValue(is("cherry_drawer"))).check(matches(isDisplayed()));
                scene.onActivity(activity -> assertEquals(1, owners(activity)));
                onView(withText(R.string.background)).perform(click());
                pressBack();
                onView(withText(R.string.background)).check(matches(hasFocus()));
            } else {
                assertEquals(1, monitor.getHits());
                scene.onActivity(activity -> assertEquals(0, owners(activity)));
            }
        } finally { instrumentation.removeMonitor(monitor); }
    }

    private Bitmap hardwareIcon(DrawerIcon icon, int size) throws InterruptedException {
        HandlerThread thread = new HandlerThread("drawer-icon-capture");
        thread.start();
        HardwareRenderer renderer = new HardwareRenderer();
        try (ImageReader reader = ImageReader.newInstance(size, size, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)) {
            CountDownLatch ready = new CountDownLatch(1);
            reader.setOnImageAvailableListener(owner -> ready.countDown(), new Handler(thread.getLooper()));
            RenderNode node = new RenderNode("drawer-icon-edge-fixture");
            node.setPosition(0, 0, size, size);
            Canvas canvas = node.beginRecording();
            icon.draw(canvas);
            node.endRecording();
            renderer.setOpaque(false);
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            assertTrue("Actual GPU icon frame must become available", ready.await(5, TimeUnit.SECONDS));
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
    @Test public void hardwareSidebarEdgesRetainSmoothCoverageAtNativeAndFourKSize()
            throws InterruptedException {
        for (int size : new int[] {24, 96}) {
            for (int kind = DrawerIcon.BACKGROUND; kind <= DrawerIcon.BACK; kind++) {
                DrawerIcon icon = new DrawerIcon(context, kind, size);
                Bitmap expected = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                icon.draw(new Canvas(expected));
                Bitmap actual = hardwareIcon(icon, size);
                try {
                    int edges = 0;
                    double error = 0;
                    for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                        int coverage = Color.alpha(expected.getPixel(x, y));
                        if (coverage <= 16 || coverage >= 239) continue;
                        error += Math.abs(Color.alpha(actual.getPixel(x, y)) - coverage) / 255.0;
                        edges++;
                    }
                    assertTrue("Fixture must include fractional edge coverage", edges > 4);
                    assertTrue("Aliased hardware icon " + kind + " at " + size
                                    + "px: mean edge coverage error " + error / edges,
                            error / edges < .12);
                } finally {
                    expected.recycle();
                    actual.recycle();
                }
            }
        }
    }

    private int owners(AuroraActivity activity) {
        try {
            java.lang.reflect.Field field = AuroraActivity.class.getDeclaredField("openPanels");
            field.setAccessible(true);
            return ((java.util.Set<?>) field.get(activity)).size();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test public void largeTextKeepsClockAndActionsInsideTheirBounds() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                android.content.res.Configuration config = activity.getResources().getConfiguration();
                float original = config.fontScale;
                CherryDrawer drawer;
                try {
                    config.fontScale = 2;
                    drawer = new CherryDrawer(activity, () -> { }, () -> { });
                } finally { config.fontScale = original; }
                java.util.List<View> tiles = new java.util.ArrayList<>();
                for (int i = 0; i < 9; i++)
                    tiles.add(drawer.button(R.string.background, DrawerIcon.BACKGROUND, () -> { }));
                drawer.main(drawer.grid(tiles), tiles.get(0), 0);
                drawer.measure(View.MeasureSpec.makeMeasureSpec(drawer.u(300), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(drawer.u(508), View.MeasureSpec.EXACTLY));
                drawer.layout(0, 0, drawer.getMeasuredWidth(), drawer.getMeasuredHeight());
                android.widget.LinearLayout header = (android.widget.LinearLayout) drawer.getChildAt(0);
                View clocksAndActions = ((android.view.ViewGroup) header).getChildAt(0);
                assertTrue(clocksAndActions.getHeight() <= header.getHeight());
            });
        }
    }

    @Test public void subpagesKeepOnePanelOwnerAndProfileFailureDoesNotDismissIt() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                "com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity",
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            menu();
            scene.onActivity(activity -> assertEquals(1, owners(activity)));
            onView(withText(R.string.background)).perform(pressKey(KeyEvent.KEYCODE_DPAD_CENTER));
            scene.onActivity(activity -> assertEquals(1, owners(activity)));
            pressBack();
            scene.onActivity(activity -> assertEquals(1, owners(activity)));
            onView(withText(R.string.background)).perform(pressKey(KeyEvent.KEYCODE_DPAD_UP));
            onView(withContentDescription(context.getString(R.string.system_settings)))
                    .perform(pressKey(KeyEvent.KEYCODE_DPAD_RIGHT));
            onView(withContentDescription("Switch Google TV profile"))
                    .perform(pressKey(KeyEvent.KEYCODE_DPAD_CENTER));
            if (monitor.getHits() == 0) {
                onView(withTagValue(is("cherry_drawer"))).check(matches(isDisplayed()));
                scene.onActivity(activity -> assertEquals(1, owners(activity)));
                pressBack();
            }
            scene.onActivity(activity -> assertEquals(0, owners(activity)));
        } finally { instrumentation.removeMonitor(monitor); }
    }
}
