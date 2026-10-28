package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class BlossomTile extends FrameLayout {
    static final int SIZE = 119;
    static final float FOCUS_SCALE = 1.14f;
    static final float MAX_FOCUS_SCALE = 1.17f;
    final ImageView flower, mark;
    private final ValueAnimator focusAnimator = ValueAnimator.ofFloat(0f, 1f);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float angle;
    private float focusStartScale, pulse;
    private boolean active;
    private Bitmap white, colour;

    BlossomTile(Context context, BrandShortcut brand, Bitmap blossom, float angle, Runnable focused) {
        super(context);
        this.angle = angle;
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription(brand.label);
        flower = new ImageView(context);
        flower.setScaleType(ImageView.ScaleType.FIT_CENTER);
        flower.setImageBitmap(blossom);
        flower.setRotation(angle);
        addView(flower, new LayoutParams(SIZE, SIZE, Gravity.CENTER));
        mark = brand == BrandShortcut.ALL
                ? new OutputResolutionIconView(context) : new ImageView(context);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (brand == BrandShortcut.ALL) mark.setImageResource(R.drawable.aurora_apps);
        addView(mark, new LayoutParams(Math.round(brand.width * SIZE * 1.1f / 125f),
                Math.round(brand.height * SIZE * 1.1f / 125f), Gravity.CENTER));
        pulsePaint.setShader(new RadialGradient(SIZE / 2f, SIZE / 2f, 37f,
                0xFFFFD396, 0x00FFD396, Shader.TileMode.CLAMP));
        focusAnimator.setDuration(700);
        focusAnimator.setInterpolator(new LinearInterpolator());
        focusAnimator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            float scale = focusScale(progress, focusStartScale);
            setScaleX(scale);
            setScaleY(scale);
            pulse = focusPulse(progress);
            invalidate();
        });
        setOnFocusChangeListener((view, hasFocus) -> {
            focusAnimator.cancel();
            animate().cancel();
            flower.animate().cancel();
            pulse = 0f;
            boolean motion = active && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled();
            if (motion) {
                if (hasFocus) {
                    focusStartScale = getScaleX();
                    focusAnimator.start();
                } else animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                flower.animate().rotation(angle + (hasFocus ? 8f : 0f)).setDuration(220).start();
            } else finishFocus();
            invalidate();
            if (brand == BrandShortcut.ALL) {
                mark.setImageTintList(ColorStateList.valueOf(hasFocus ? 0xFF0779FF : Color.WHITE));
            } else mark.setImageBitmap(hasFocus ? colour : white);
            if (hasFocus) focused.run();
        });
    }

    static float focusScale(float progress, float startScale) {
        return progress <= .4f
                ? startScale + (MAX_FOCUS_SCALE - startScale) * smoother(progress / .4f)
                : MAX_FOCUS_SCALE - (MAX_FOCUS_SCALE - FOCUS_SCALE) * smoother((progress - .4f) / .6f);
    }

    static float focusPulse(float progress) {
        if (progress <= 0f || progress >= 1f) return 0f;
        double wave = Math.sin(progress * Math.PI);
        return (float) (.12 * wave * wave);
    }

    private static float smoother(float value) {
        float p = Math.max(0f, Math.min(1f, value));
        return p * p * p * (p * (p * 6f - 15f) + 10f);
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (!active) finishFocus();
    }

    private void finishFocus() {
        focusAnimator.cancel();
        animate().cancel();
        flower.animate().cancel();
        setScaleX(hasFocus() ? FOCUS_SCALE : 1f);
        setScaleY(hasFocus() ? FOCUS_SCALE : 1f);
        flower.setRotation(angle + (hasFocus() ? 8f : 0f));
        pulse = 0f;
        invalidate();
    }

    @Override protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = super.drawChild(canvas, child, drawingTime);
        if (child == flower && pulse > 0f) {
            pulsePaint.setAlpha(Math.round(pulse * 255));
            canvas.drawCircle(SIZE / 2f, SIZE / 2f, 37f, pulsePaint);
        }
        return more;
    }

    @Override protected void onDetachedFromWindow() {
        finishFocus();
        super.onDetachedFromWindow();
    }

    void setLogos(Bitmap white, Bitmap colour) {
        this.white = white;
        this.colour = colour;
        mark.setImageBitmap(hasFocus() ? colour : white);
    }
}
