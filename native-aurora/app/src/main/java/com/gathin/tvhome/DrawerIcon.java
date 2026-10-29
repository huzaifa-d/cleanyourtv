package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class DrawerIcon extends Drawable {
    static final int BACKGROUND = 0, RAM = 1, SCREENSAVER = 2, INPUTS = 3, BLUETOOTH = 4,
            ACCESSIBILITY = 5, APPS = 6, HOME = 7, GOOGLE_HOME = 8, SETTINGS = 9, PROFILE = 10, BACK = 11;
    private static final int[] RESOURCES = {
            R.drawable.ic_drawer_background, R.drawable.ic_drawer_ram,
            R.drawable.ic_drawer_screensaver, R.drawable.ic_drawer_inputs,
            R.drawable.ic_drawer_bluetooth, R.drawable.ic_drawer_accessibility,
            R.drawable.ic_drawer_apps, R.drawable.ic_drawer_home, R.drawable.ic_drawer_google_home,
            R.drawable.ic_drawer_settings, R.drawable.ic_drawer_profile, R.drawable.ic_drawer_back};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Drawable vector;
    private final int size;
    private int color = CherryDrawer.TEXT;
    private Bitmap coverage;

    DrawerIcon(Context context, int kind, int size) {
        if (kind < 0 || kind >= RESOURCES.length) throw new IllegalArgumentException("Unknown drawer icon");
        this.size = size;
        vector = context.getDrawable(RESOURCES[kind]).mutate();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        setBounds(0, 0, size, size);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        int width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) {
            coverage = null;
        } else if (coverage == null || coverage.getWidth() != width || coverage.getHeight() != height) {
            // Preserve software edge coverage on hardware canvases that alias direct paths.
            coverage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            vector.setBounds(0, 0, width, height);
            vector.draw(new Canvas(coverage));
        }
    }

    @Override public void draw(Canvas canvas) {
        if (coverage != null) canvas.drawBitmap(coverage, null, getBounds(), paint);
    }

    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
        int next = CherryDrawer.TEXT;
        for (int state : states) if (state == android.R.attr.state_focused
                || state == android.R.attr.state_pressed) next = CherryDrawer.FOCUSED_TEXT;
        if (color == next) return false;
        color = next;
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        invalidateSelf();
        return true;
    }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
