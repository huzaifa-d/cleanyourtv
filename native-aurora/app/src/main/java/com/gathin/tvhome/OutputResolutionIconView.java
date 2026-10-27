package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

final class OutputResolutionIconView extends ImageView {
    private int resource, renderedResource, renderedWidth, renderedHeight;
    private float outputScale = 1f;

    OutputResolutionIconView(Context context) { super(context); }

    @Override public void setImageResource(int resource) {
        this.resource = resource;
        render();
    }

    void setOutputScale(float scale) {
        outputScale = scale;
        render();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        render();
    }

    private void render() {
        int width = (int) Math.ceil((getWidth() - getPaddingLeft() - getPaddingRight()) * outputScale);
        int height = (int) Math.ceil((getHeight() - getPaddingTop() - getPaddingBottom()) * outputScale);
        if (resource == 0 || width <= 0 || height <= 0) return;
        if (renderedResource == resource && renderedWidth == width && renderedHeight == height) return;
        // Parent/focus transforms happen after vector caching; retain those output pixels here.
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Drawable drawable = getContext().getDrawable(resource).mutate();
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        renderedResource = resource;
        renderedWidth = width;
        renderedHeight = height;
        setImageBitmap(bitmap);
    }
}
