package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class BlossomTile extends FrameLayout {
    static final int SIZE = 119;
    static final float FOCUS_SCALE = 1.14f;
    final ImageView flower, mark;
    private Bitmap white, colour;

    BlossomTile(Context context, BrandShortcut brand, Bitmap blossom, float angle, Runnable focused) {
        super(context);
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
        setOnFocusChangeListener((view, hasFocus) -> {
            animate().cancel();
            animate().scaleX(hasFocus ? FOCUS_SCALE : 1f).scaleY(hasFocus ? FOCUS_SCALE : 1f)
                    .setDuration(180).start();
            flower.animate().cancel();
            flower.animate().rotation(angle + (hasFocus ? 8f : 0f)).setDuration(220).start();
            if (brand == BrandShortcut.ALL) {
                mark.setImageTintList(ColorStateList.valueOf(hasFocus ? 0xFF0779FF : Color.WHITE));
            } else mark.setImageBitmap(hasFocus ? colour : white);
            if (hasFocus) focused.run();
        });
    }

    void setLogos(Bitmap white, Bitmap colour) {
        this.white = white;
        this.colour = colour;
        mark.setImageBitmap(hasFocus() ? colour : white);
    }
}
