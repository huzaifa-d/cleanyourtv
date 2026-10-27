package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraPlaybackTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File video;
    @Before public void copySyntheticFixture() throws Exception {
        File directory = new File(context.getExternalFilesDir(null), "backgrounds");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        video = new File(directory, "playback-test.mp4");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("playback-test.mp4"); FileOutputStream output = new FileOutputStream(video)) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        context.getSharedPreferences("aurora", 0).edit().clear().putString("video", video.getName())
                .putFloat("speed", 0.5f).commit();
    }
    @After public void cleanup() {
        context.getSharedPreferences("aurora", 0).edit().clear().commit();
        if (video != null) video.delete();
    }
    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static ExoPlayer player(AuroraActivity activity) {
        Object backend = field(field(activity, "background"), "player");
        return backend == null ? null : (ExoPlayer) field(backend, "player");
    }
    private void await(ActivityScenario<AuroraActivity> scene, Predicate<AuroraActivity> condition) {
        AtomicBoolean met = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 20000;
        do {
            scene.onActivity(activity -> met.set(condition.test(activity)));
            if (met.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Playback did not reach the expected lifecycle state");
    }
    private void key(int code) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                .perform(androidx.test.espresso.action.ViewActions.pressKey(code));
    }
    @Test public void localFileIgnoresLegacyHalfSpeedAndReleasesBehindPanelAndStoppedActivity() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            await(scene, activity -> activity.findViewById(R.id.background_still).getVisibility() == View.INVISIBLE);
            AtomicReference<ExoPlayer> first = new AtomicReference<>();
            AtomicReference<AuroraActivity> owner = new AtomicReference<>();
            scene.onActivity(activity -> {
                owner.set(activity);
                first.set(player(activity));
                assertNotNull(first.get());
                assertTrue(first.get().isPlaying());
                assertEquals(Player.REPEAT_MODE_ALL, first.get().getRepeatMode());
                assertEquals(1f, first.get().getPlaybackParameters().speed, 0.01f);
            });
            SystemClock.sleep(4500);
            scene.onActivity(activity -> {
                assertSame(first.get(), player(activity));
                assertTrue(player(activity).isPlaying());
                assertTrue(player(activity).getCurrentPosition() <= player(activity).getDuration());
            });
            key(KeyEvent.KEYCODE_MENU);
            scene.onActivity(activity -> {
                assertNull(player(activity));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.background_still).getVisibility());
            });
            key(KeyEvent.KEYCODE_BACK);
            await(scene, activity -> activity.findViewById(R.id.background_still).getVisibility() == View.INVISIBLE);
            scene.onActivity(activity -> assertNotSame(first.get(), player(activity)));
            scene.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertNull(player(owner.get())));
            scene.moveToState(Lifecycle.State.RESUMED);
            await(scene, activity -> activity.findViewById(R.id.background_still).getVisibility() == View.INVISIBLE);
        }
    }
    @Test public void corruptFileFallsBackAndSettingsRemainReachable() throws Exception {
        try (FileOutputStream corrupt = new FileOutputStream(video)) { corrupt.write(new byte[] {1, 2, 3}); }
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            await(scene, activity -> {
                Object session = field(activity, "background");
                return video.getAbsolutePath().equals(field(session, "source"))
                        && field(session, "player") == null;
            });
            scene.onActivity(activity -> assertEquals(
                    View.VISIBLE, activity.findViewById(R.id.background_still).getVisibility()));
            key(KeyEvent.KEYCODE_MENU);
            androidx.test.espresso.Espresso.onView(
                    androidx.test.espresso.matcher.ViewMatchers.withId(R.id.ram_switch))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                            androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        }
    }

    @Test public void bundledBackgroundDefaultsToOneSpeedAndRestartsAfterPanel() {
        context.getSharedPreferences("aurora", 0).edit().clear().commit();
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            await(scene, activity -> activity.findViewById(R.id.background_still).getVisibility() == View.INVISIBLE);
            AtomicReference<ExoPlayer> first = new AtomicReference<>();
            long[] position = new long[1];
            scene.onActivity(activity -> {
                first.set(player(activity));
                assertNotNull(first.get());
                assertTrue(first.get().isPlaying());
                assertEquals(Player.REPEAT_MODE_ALL, first.get().getRepeatMode());
                assertEquals(1f, first.get().getPlaybackParameters().speed, 0.01f);
                assertEquals(12000, first.get().getDuration(), 100);
                position[0] = first.get().getCurrentPosition();
            });
            SystemClock.sleep(1200);
            scene.onActivity(activity -> {
                assertSame(first.get(), player(activity));
                assertTrue(first.get().getCurrentPosition() > position[0]);
            });
            key(KeyEvent.KEYCODE_MENU);
            scene.onActivity(activity -> assertNull(player(activity)));
            key(KeyEvent.KEYCODE_BACK);
            await(scene, activity -> activity.findViewById(R.id.background_still).getVisibility() == View.INVISIBLE);
            scene.onActivity(activity -> {
                assertNotSame(first.get(), player(activity));
                assertTrue(player(activity).isPlaying());
            });
        }
    }

    @Test public void repeatsAtOneSpeedWithoutRebufferingOrReinitializingDecoder() {
        context.getSharedPreferences("aurora", 0).edit().putFloat("speed", 2f).commit();
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            await(scene, activity -> activity.findViewById(R.id.background_still).getVisibility() == View.INVISIBLE);
            AtomicReference<ExoPlayer> first = new AtomicReference<>();
            AtomicInteger transitions = new AtomicInteger();
            AtomicInteger buffering = new AtomicInteger();
            scene.onActivity(activity -> {
                ExoPlayer current = player(activity);
                first.set(current);
                assertEquals(1f, current.getPlaybackParameters().speed, 0.01f);
                assertEquals(1, current.getVideoDecoderCounters().decoderInitCount);
                current.addListener(new Player.Listener() {
                    @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) transitions.incrementAndGet();
                    }
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_BUFFERING) buffering.incrementAndGet();
                    }
                });
            });
            await(scene, activity -> transitions.get() >= 3 && player(activity) != null
                    && player(activity).isPlaying());
            scene.onActivity(activity -> {
                assertSame(first.get(), player(activity));
                assertTrue(player(activity).isPlaying());
                assertEquals("Unexpected loop rebuffering; decoder initializations="
                        + player(activity).getVideoDecoderCounters().decoderInitCount, 0, buffering.get());
                assertEquals(1, player(activity).getVideoDecoderCounters().decoderInitCount);
                assertEquals(0, player(activity).getVideoDecoderCounters().decoderReleaseCount);
            });
        }
    }
}
