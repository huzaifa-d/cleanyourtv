package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RainWeatherTest {
    private final long now = Instant.parse("2026-10-29T19:30:00Z").toEpochMilli();

    private String response(String time, String rain, String showers, int code) {
        return "{\"utc_offset_seconds\":-25200,\"current\":{\"time\":\"" + time
                + "\",\"rain\":" + rain + ",\"showers\":" + showers + ",\"weather_code\":" + code + "}}";
    }

    @Test public void rainShowersAndRoundedDrizzleEnableRainButSnowAndCloudDoNot() throws Exception {
        assertTrue(RainWeather.parse(response("2026-10-29T12:30", "0.1", "0", 3), now).raining);
        assertTrue(RainWeather.parse(response("2026-10-29T12:30", "0", "0.1", 3), now).raining);
        assertTrue(RainWeather.parse(response("2026-10-29T12:30", "0", "0", 51), now).raining);
        for (int code : new int[] {0, 3, 45, 71, 85})
            assertFalse(RainWeather.parse(response("2026-10-29T12:30", "0", "0", code), now).raining);
    }

    @Test public void staleFutureAndMissingWeatherAreRejectedInsteadOfInventingDryWeather() {
        for (String json : new String[] {
                response("2026-10-29T11:00", "1", "0", 61),
                response("2026-10-29T14:00", "1", "0", 61),
                response("2026-10-29T12:30", "null", "0", 61),
                response("2026-10-29T12:30", "-1", "0", 61), "{}"}) {
            try {
                RainWeather.parse(json, now);
                fail("Invalid or stale weather must not be accepted");
            } catch (IOException expected) { }
        }
    }

    @Test public void cachedRainExpiresWithoutRequiringAnotherSuccessfulRequest() {
        assertTrue(RainWeather.fresh(now, now + RainWeather.MAX_AGE - 1));
        assertFalse(RainWeather.fresh(now, now + RainWeather.MAX_AGE));
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    public void cachedWeatherSurvivesResumeWithoutDuplicateFetchesOrHiddenTimers() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences cache = base.getSharedPreferences("rain_weather_lifecycle_test", 0);
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return cache;
            }
        };
        long attempted = System.currentTimeMillis();
        cache.edit().clear().putLong("observed", attempted).putBoolean("rain", true)
                .putLong("last_attempt", attempted).commit();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Handler handler = new Handler(Looper.getMainLooper());
                AtomicInteger notifications = new AtomicInteger();
                RainWeather weather = new RainWeather(isolated, handler, notifications::incrementAndGet);
                try {
                    Field callback = RainWeather.class.getDeclaredField("tick");
                    callback.setAccessible(true);
                    Runnable tick = (Runnable) callback.get(weather);
                    Field fetching = RainWeather.class.getDeclaredField("fetching");
                    fetching.setAccessible(true);
                    assertTrue("Fresh cached rain must apply immediately", weather.raining());
                    for (int i = 0; i < 3; i++) {
                        weather.setActive(true);
                        weather.setActive(true);
                        assertEquals(i + 1, notifications.get());
                        assertTrue(handler.hasCallbacks(tick));
                        assertFalse("Resume must respect the persisted request limit",
                                fetching.getBoolean(weather));
                        assertEquals(attempted, cache.getLong("last_attempt", 0));
                        weather.setActive(false);
                        assertFalse("Hidden Home must have no weather timer", handler.hasCallbacks(tick));
                    }
                    weather.close();
                    weather.setActive(true);
                    assertFalse(handler.hasCallbacks(tick));
                    assertEquals(3, notifications.get());
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                finally { weather.close(); }
            });
        } finally { cache.edit().clear().commit(); }
    }
}
