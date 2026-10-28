package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraIconResolutionTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static void layout(AuroraHomeView home, int width, int height) {
        home.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        home.layout(0, 0, width, height);
    }

    private static Bitmap bitmap(ImageView icon) {
        assertTrue("Icon must retain output-resolution pixels instead of an enlarged vector cache",
                icon.getDrawable() instanceof BitmapDrawable);
        return ((BitmapDrawable) icon.getDrawable()).getBitmap();
    }

    private static ImageView apps(AuroraHomeView home) {
        return ((BlossomTile) home.findViewById(R.id.all_apps)).mark;
    }

    @Test public void nativeIconsCoverOutputPixelsIncludingFocusAndReuseUnchangedRasters() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AuroraHomeView home = new AuroraHomeView(context, new HomeNavigation(), ignored -> { });
            for (int[] output : new int[][] {{960, 540}, {1280, 720}, {1920, 1080}, {3840, 2160}}) {
                layout(home, output[0], output[1]);
                float scale = output[0] / 960f;
                ImageView profile = home.findViewById(R.id.profile);
                assertEquals((int) Math.ceil(17 * scale), bitmap(profile).getWidth());
                assertEquals((int) Math.ceil(17 * scale), bitmap(profile).getHeight());
                ImageView all = apps(home);
                assertEquals((int) Math.ceil(all.getWidth() * 1.14f * scale), bitmap(all).getWidth());
                assertEquals((int) Math.ceil(all.getHeight() * 1.14f * scale), bitmap(all).getHeight());
                for (NetworkMonitor.Status status : NetworkMonitor.Status.values()) {
                    home.setNetworkStatus(status);
                    ImageView network = home.findViewById(R.id.network_status);
                    Bitmap original = bitmap(network);
                    assertEquals((int) Math.ceil(18 * scale), original.getWidth());
                    assertEquals((int) Math.ceil(18 * scale), original.getHeight());
                    home.setNetworkStatus(status);
                    layout(home, output[0], output[1]);
                    assertSame("Unchanged status/layout must not allocate another bitmap",
                            original, bitmap(network));
                }
                Bitmap original = bitmap(all);
                home.applyFocus(BrandShortcut.ALL.ordinal());
                assertSame(original, bitmap(all));
            }
        });
    }

    @Test public void everyBrandHasEnoughPixelsForFocusedFourKHome() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AuroraHomeView home = new AuroraHomeView(context, new HomeNavigation(), ignored -> { });
            layout(home, 3840, 2160);
            for (BrandShortcut brand : BrandShortcut.values()) {
                if (brand == BrandShortcut.ALL) continue;
                ImageView mark = ((BlossomTile) home.findViewById(
                        AuroraHomeView.TILE_IDS[brand.ordinal()])).mark;
                assertTrue(brand.label + " is being enlarged beyond its decoded pixels",
                        bitmap(mark).getWidth() >= mark.getWidth() * 1.14f * 4);
                Bitmap idle = bitmap(mark);
                home.applyFocus(brand.ordinal());
                assertTrue(home.findViewById(AuroraHomeView.TILE_IDS[brand.ordinal()]).hasFocus());
                assertNotSame(idle, bitmap(mark));
                assertTrue(brand.label + " focused artwork also needs output-resolution pixels",
                        bitmap(mark).getWidth() >= mark.getWidth() * 1.14f * 4);
                if (brand == BrandShortcut.DISNEY) {
                    Bitmap focused = bitmap(mark);
                    boolean gradient = false;
                    for (int y = 0; y < focused.getHeight() && !gradient; y++) {
                        for (int x = 0; x < focused.getWidth(); x++) {
                            int pixel = focused.getPixel(x, y);
                            if (Color.alpha(pixel) > 128 && Color.blue(pixel) > 180
                                    && Color.green(pixel) > 60 && Color.red(pixel) < 80) {
                                gradient = true;
                                break;
                            }
                        }
                    }
                    assertTrue("The full old Disney+ logo must retain its blue/cyan gradient arc", gradient);
                }
            }
        });
    }

    @Test public void libraryIconsUseRequestedPixelsWithoutReusingSmallerCachedImages() throws Exception {
        AppCatalog catalog = new AppCatalog(context);
        AppCatalog.Entry entry = new AppCatalog.Entry(context.getPackageName(), "Aurora",
                new ComponentName(context, AuroraActivity.class));
        try {
            Bitmap small = load(catalog, entry, 116);
            assertNotNull(small);
            assertEquals(116, small.getWidth());
            assertEquals(116, small.getHeight());
            Bitmap large = load(catalog, entry, 232);
            assertNotNull(large);
            assertEquals(232, large.getWidth());
            assertEquals(232, large.getHeight());
            assertNotSame(small, large);
            assertSame(small, load(catalog, entry, 116));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(catalog::close);
        }
    }

    private Bitmap load(AppCatalog catalog, AppCatalog.Entry entry, int size) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Bitmap> loaded = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                catalog.icon(entry, size, result -> {
                    loaded.set(result);
                    completed.countDown();
                }));
        assertTrue("Icon worker did not finish", completed.await(10, TimeUnit.SECONDS));
        return loaded.get();
    }
}
