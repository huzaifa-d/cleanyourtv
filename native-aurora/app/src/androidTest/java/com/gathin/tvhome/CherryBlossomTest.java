package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
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

    private static Bitmap draw(PetalView view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private void key(int code) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                .perform(androidx.test.espresso.action.ViewActions.pressKey(code));
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
