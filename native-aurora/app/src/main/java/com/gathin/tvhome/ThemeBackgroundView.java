package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class ThemeBackgroundView extends View {
    static final float MOON_X = 691f, MOON_Y = 81f, MOON_RADIUS = 19.55f;
    final ThemeScene scene = new ThemeScene();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF backgroundBounds = new RectF(0, 0, 960, 540);
    private final RectF moonBounds = new RectF(-MOON_RADIUS, -MOON_RADIUS, MOON_RADIUS, MOON_RADIUS);
    private final Path lit = new Path();
    private final RectF terminator = new RectF();
    private final RectF renderedBounds = new RectF();
    private final Bitmap moon;
    private final LongSupplier clock;
    private final Supplier<ZoneId> zone;
    private Bitmap day, night, dayForeground, nightForeground;
    private PetalView petals;
    private final RainPainter rainPainter = new RainPainter();
    private boolean rain;
    private long rainMillis;
    private Bitmap renderedMoon;
    private Canvas moonCanvas;
    private double renderedIllumination = Double.NaN;
    private boolean renderedWaxing;
    private int outputWidth = 960;
    private boolean active, defaultSelected = true, videoVisible, running;
    private long lastFrame;
    private DoubleConsumer nightBlendChanged;
    private double reportedNightBlend = Double.NaN;
    private final Runnable frame = () -> {
        if (!running) return;
        advanceVisible();
        refreshArtwork();
        invalidate();
        schedule();
    };

    ThemeBackgroundView(Context context) {
        this(context, System::currentTimeMillis, ZoneId::systemDefault);
    }

    ThemeBackgroundView(Context context, LongSupplier clock, Supplier<ZoneId> zone) {
        this(context, clock, zone, null);
    }

    ThemeBackgroundView(Context context, LongSupplier clock, Supplier<ZoneId> zone, Bitmap texture) {
        super(context);
        this.clock = clock;
        this.zone = zone;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        moon = texture == null ? decode("aurora/cherry/moon.png", 512) : texture;
        if (moon != null) {
            BitmapShader shader = new BitmapShader(moon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.setScale(-2 * MOON_RADIUS / moon.getWidth(), -2 * MOON_RADIUS / moon.getHeight());
            matrix.postTranslate(MOON_RADIUS, MOON_RADIUS);
            shader.setLocalMatrix(matrix);
            moonPaint.setShader(shader);
        }
        scene.update(clock.getAsLong(), zone.get(), ValueAnimator.areAnimatorsEnabled());
    }

    void setNightBlendListener(DoubleConsumer listener) {
        nightBlendChanged = listener;
        reportNightBlend(true);
    }

    void setOutputWidth(int width) {
        int bounded = Math.max(960, Math.min(3840, width));
        if (outputWidth != bounded) {
            outputWidth = bounded;
            day = null;
            night = null;
            dayForeground = null;
            nightForeground = null;
        }
        refreshArtwork();
    }

    void setActive(boolean value) {
        if (active == value) return;
        advanceVisible();
        active = value;
        if (value) scene.update(clock.getAsLong(), zone.get(),
                ValueAnimator.areAnimatorsEnabled());
        reconcile();
    }

    void setDefaultSelected(boolean value) {
        if (defaultSelected == value) return;
        advanceVisible();
        defaultSelected = value;
        reconcile();
    }

    void setVideoVisible(boolean value) {
        advanceVisible();
        videoVisible = value;
        setVisibility(value ? INVISIBLE : VISIBLE);
        reconcile();
    }

    void updateTime() {
        advanceVisible();
        scene.update(clock.getAsLong(), zone.get(), ValueAnimator.areAnimatorsEnabled());
        reconcile();
    }

    void setRain(boolean value) {
        if (rain == value) return;
        advanceVisible();
        rain = value;
        reconcile();
    }

    void setPetals(PetalView view) {
        petals = view;
        invalidate();
    }

    void save(Bundle state) {
        advanceVisible();
        state.putDoubleArray("theme_scene", scene.save());
        state.putLong("rain_millis", rainMillis);
    }

    void restore(Bundle state) {
        if (state != null) {
            scene.restore(state.getDoubleArray("theme_scene"));
            rainMillis = Math.floorMod(state.getLong("rain_millis"), 12000);
        }
        updateTime();
    }

    private void advanceVisible() {
        long now = SystemClock.uptimeMillis();
        if (running) {
            boolean animate = ValueAnimator.areAnimatorsEnabled();
            scene.advance(now - lastFrame, animate);
            if (rain && animate) rainMillis = (rainMillis + now - lastFrame) % 12000;
        }
        lastFrame = now;
        reportNightBlend(false);
    }

    private void reconcile() {
        removeCallbacks(frame);
        running = active && defaultSelected && !videoVisible && isAttachedToWindow();
        refreshArtwork();
        reportNightBlend(false);
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
        schedule();
    }

    private void schedule() {
        removeCallbacks(frame);
        if (!running) return;
        if (rain && ValueAnimator.areAnimatorsEnabled()) {
            postOnAnimation(frame);
            return;
        }
        if (!scene.pending()) return;
        long delay = scene.nextDelay();
        if (delay > 0) postDelayed(frame, delay);
        else postOnAnimation(frame);
    }

    private void refreshArtwork() {
        double blend = defaultSelected ? scene.nightBlend : scene.night ? 1 : 0;
        boolean needsDay = blend < 1 || !scene.night;
        boolean needsNight = blend > 0 || scene.night;
        if (needsDay && day == null) {
            day = decode("aurora/cherry/day.webp", outputWidth);
            dayForeground = decode("aurora/cherry/day-foreground.webp", outputWidth);
        }
        if (needsNight && night == null) {
            night = decode("aurora/cherry/night.webp", outputWidth);
            nightForeground = decode("aurora/cherry/night-foreground.webp", outputWidth);
        }
        if (!needsDay) { day = null; dayForeground = null; }
        if (!needsNight) { night = null; nightForeground = null; }
    }

    private Bitmap decode(String path, int width) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = getContext().getAssets().open(path)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            int sample = 1;
            while (options.outWidth / (sample * 2) >= width) sample *= 2;
            options.inSampleSize = sample;
            options.inJustDecodeBounds = false;
            try (InputStream input = getContext().getAssets().open(path)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (IOException failure) {
            Log.w("Aurora", "Theme artwork unavailable: " + path, failure);
            if (!path.equals("aurora/cherry-blossom.jpg") && !path.endsWith("moon.png")
                    && !path.endsWith("-foreground.webp"))
                return decode("aurora/cherry-blossom.jpg", width);
            return null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTime();
    }

    @Override protected void onDetachedFromWindow() {
        advanceVisible();
        running = false;
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double blend = defaultSelected ? scene.nightBlend : scene.night ? 1 : 0;
        if (blend < 1) drawScene(canvas, false);
        if (blend > 0) {
            int saved = blend < 1 ? canvas.saveLayerAlpha(backgroundBounds,
                    (int) Math.round(blend * 255)) : canvas.save();
            drawScene(canvas, true);
            canvas.restoreToCount(saved);
        }
    }

    private void drawScene(Canvas canvas, boolean atNight) {
        paint.setAlpha(255);
        Bitmap background = atNight ? night : day;
        if (background != null) canvas.drawBitmap(background, null, backgroundBounds, paint);
        if (moon != null && defaultSelected && atNight) {
            int translated = canvas.save();
            canvas.translate(MOON_X, MOON_Y);
            renderMoon();
            paint.setAlpha(255);
            canvas.drawBitmap(renderedMoon, null, renderedBounds, paint);
            canvas.restoreToCount(translated);
        }
        if (rain && defaultSelected && ValueAnimator.areAnimatorsEnabled())
            rainPainter.draw(canvas, rainMillis, atNight, outputWidth);
        if (petals != null) petals.draw(canvas);
        Bitmap foreground = atNight ? nightForeground : dayForeground;
        paint.setAlpha(255);
        if (foreground != null) canvas.drawBitmap(foreground, null, backgroundBounds, paint);
    }

    private void renderMoon() {
        float scale = outputWidth / 960f;
        int size = 2 * (int) Math.ceil(MOON_RADIUS * scale);
        double illumination = scene.illumination();
        boolean waxing = scene.phase.waxing;
        if (renderedMoon == null || renderedMoon.getWidth() != size) {
            renderedMoon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            moonCanvas = new Canvas(renderedMoon);
            float radius = size / (2 * scale);
            renderedBounds.set(-radius, -radius, radius, radius);
            renderedIllumination = Double.NaN;
        }
        if (illumination == renderedIllumination && waxing == renderedWaxing) return;
        // A tiny output-resolution buffer keeps edge coverage consistent across TV GPU backends.
        renderedMoon.eraseColor(android.graphics.Color.TRANSPARENT);
        int saved = moonCanvas.save();
        moonCanvas.translate(size / 2f, size / 2f);
        moonCanvas.scale(scale, scale);
        paint.setAlpha(26);
        drawTexture(moonCanvas);
        buildLightPath(illumination, waxing);
        moonCanvas.drawPath(lit, moonPaint);
        moonCanvas.restoreToCount(saved);
        renderedIllumination = illumination;
        renderedWaxing = waxing;
    }

    private void drawTexture(Canvas canvas) {
        int saved = canvas.save();
        canvas.rotate(180);
        canvas.drawBitmap(moon, null, moonBounds, paint);
        canvas.restoreToCount(saved);
    }

    private void buildLightPath(double illumination, boolean waxing) {
        lit.rewind();
        if (illumination <= 0) return;
        if (illumination >= 1) {
            lit.addOval(moonBounds, Path.Direction.CW);
            return;
        }
        lit.moveTo(0, -MOON_RADIUS);
        lit.arcTo(moonBounds, -90, waxing ? -180 : 180);
        float width = (float) (2 * illumination - 1) * MOON_RADIUS;
        if (Math.abs(width) < .00001f) lit.lineTo(0, -MOON_RADIUS);
        else {
            terminator.set(-Math.abs(width), -MOON_RADIUS, Math.abs(width), MOON_RADIUS);
            boolean right = waxing ? width > 0 : width < 0;
            lit.arcTo(terminator, 90, right ? -180 : 180);
        }
        lit.close();
    }

    private void reportNightBlend(boolean force) {
        if (nightBlendChanged == null) return;
        double current = defaultSelected && !videoVisible ? scene.nightBlend : 0;
        if (!force && current == reportedNightBlend) return;
        reportedNightBlend = current;
        nightBlendChanged.accept(current);
    }
}
