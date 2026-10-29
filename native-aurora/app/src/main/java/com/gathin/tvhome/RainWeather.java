package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

final class RainWeather implements AutoCloseable {
    static final long REFRESH = 15 * 60000L, MAX_AGE = 30 * 60000L;
    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=47.674&longitude=-122.1215&current=rain,showers,weather_code"
            + "&timezone=America%2FLos_Angeles&forecast_days=1";
    private final Handler main;
    private final SharedPreferences cache;
    private final Runnable changed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean active, closed, fetching, wet;
    private long observed, lastAttempt;
    private final Runnable tick = this::refresh;

    RainWeather(Context context, Handler main, Runnable changed) {
        this.main = main;
        this.changed = changed;
        cache = context.getSharedPreferences("aurora_weather", Context.MODE_PRIVATE);
        observed = cache.getLong("observed", 0);
        wet = cache.getBoolean("rain", false);
        lastAttempt = cache.getLong("last_attempt", 0);
    }

    boolean raining() {
        return wet && fresh(observed, System.currentTimeMillis());
    }

    void setActive(boolean value) {
        if (closed || active == value) return;
        active = value;
        main.removeCallbacks(tick);
        if (active) refresh();
    }

    private void refresh() {
        if (!active || closed) return;
        changed.run();
        long now = System.currentTimeMillis();
        if (!fetching && (lastAttempt == 0 || now - lastAttempt >= REFRESH
                || lastAttempt - now > REFRESH)) {
            fetching = true;
            lastAttempt = now;
            cache.edit().putLong("last_attempt", now).apply();
            worker.execute(() -> {
                Snapshot result = null;
                try { result = fetch(); }
                catch (IOException | RuntimeException error) {
                    Log.w("Aurora", "Redmond weather unavailable", error);
                }
                Snapshot received = result;
                main.post(() -> {
                    if (closed) return;
                    fetching = false;
                    if (received != null) {
                        wet = received.raining;
                        observed = received.observed;
                        cache.edit().putBoolean("rain", wet).putLong("observed", observed).apply();
                    }
                    if (active) {
                        changed.run();
                        schedule();
                    }
                });
            });
        }
        schedule();
    }

    private void schedule() {
        main.removeCallbacks(tick);
        if (!active || closed) return;
        long now = System.currentTimeMillis();
        long delay = Math.max(1000, REFRESH - Math.max(0, now - lastAttempt));
        long expiry = observed + MAX_AGE - now;
        if (wet && expiry > 0) delay = Math.min(delay, expiry);
        main.postDelayed(tick, delay);
    }

    static final class Snapshot {
        final boolean raining;
        final long observed;
        Snapshot(boolean raining, long observed) {
            this.raining = raining;
            this.observed = observed;
        }
    }

    static boolean fresh(long observed, long now) {
        return observed > 0 && now - observed < MAX_AGE && observed - now <= 20 * 60000L;
    }

    static Snapshot parse(String json, long now) throws IOException {
        try {
            JSONObject root = new JSONObject(json), current = root.getJSONObject("current");
            double rain = current.getDouble("rain"), showers = current.getDouble("showers");
            if (!Double.isFinite(rain) || !Double.isFinite(showers) || rain < 0 || showers < 0)
                throw new IOException("Invalid rain amounts");
            long observed = LocalDateTime.parse(current.getString("time"))
                    .toInstant(ZoneOffset.ofTotalSeconds(root.getInt("utc_offset_seconds"))).toEpochMilli();
            if (!fresh(observed, now)) throw new IOException("Weather observation is not current");
            int code = current.getInt("weather_code");
            boolean wet = rain + showers > 0 || code == 51 || code == 53 || code == 55
                    || code == 56 || code == 57 || code == 61 || code == 63 || code == 65
                    || code == 66 || code == 67 || code == 80 || code == 81 || code == 82;
            return new Snapshot(wet, observed);
        } catch (JSONException | java.time.DateTimeException error) {
            throw new IOException("Invalid weather response", error);
        }
    }

    private static Snapshot fetch() throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "CherryLauncher/1.9");
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Weather request failed");
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 32768) throw new IOException("Weather response too large");
                    bytes.write(buffer, 0, count);
                }
                return parse(bytes.toString(StandardCharsets.UTF_8.name()), System.currentTimeMillis());
            }
        } finally { connection.disconnect(); }
    }

    @Override public void close() {
        closed = true;
        active = false;
        main.removeCallbacks(tick);
        worker.shutdownNow();
    }
}
