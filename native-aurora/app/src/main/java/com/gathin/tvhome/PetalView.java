package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

final class PetalView extends View {
    private final Bitmap[] textures = new Bitmap[6];
    private final PetalMotion[] petals = new PetalMotion[16];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private boolean active, running;
    private long elapsedMillis, lastFrame;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!ValueAnimator.areAnimatorsEnabled()) {
                running = false;
                return;
            }
            long now = SystemClock.uptimeMillis();
            elapsedMillis += now - lastFrame;
            lastFrame = now;
            invalidate();
            postOnAnimation(this);
        }
    };

    PetalView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < textures.length; i++) {
            try (InputStream input = context.getAssets().open("aurora/cherry/petal-" + (i + 1) + ".png")) {
                textures[i] = BitmapFactory.decodeStream(input);
            } catch (IOException unavailable) { textures[i] = null; }
        }
        Random random = new Random(2810);
        for (int i = 0; i < petals.length; i++) {
            petals[i] = new PetalMotion(i % textures.length, .01f + random.nextFloat() * .009f,
                    14000 + random.nextDouble() * 22000, random.nextDouble(),
                    (i + .5f) / petals.length, -.12f + random.nextFloat() * .22f,
                    .24f + random.nextFloat() * .58f, -180 + random.nextFloat() * 360);
        }
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        reconcile();
    }

    private void reconcile() {
        removeCallbacks(frame);
        running = active && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled();
        lastFrame = SystemClock.uptimeMillis();
        if (running) postOnAnimation(frame);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reconcile();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (PetalMotion petal : petals) {
            Bitmap texture = textures[petal.texture];
            if (texture == null) continue;
            petal.sample(elapsedMillis);
            float width = petal.width * getWidth();
            float height = width * texture.getHeight() / texture.getWidth();
            destination.set(-width / 2, -height / 2, width / 2, height / 2);
            paint.setAlpha(Math.round(petal.alpha * 255));
            int saved = canvas.save();
            canvas.translate(petal.x * getWidth(), petal.y * getHeight());
            canvas.rotate(petal.angle);
            canvas.drawBitmap(texture, null, destination, paint);
            canvas.restoreToCount(saved);
        }
    }
}
