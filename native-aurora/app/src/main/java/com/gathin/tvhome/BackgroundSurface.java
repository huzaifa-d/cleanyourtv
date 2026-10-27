package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import java.util.function.Consumer;

final class BackgroundSurface extends FrameLayout implements SurfaceHolder.Callback {
    private final SurfaceView surface;
    private final Consumer<Boolean> ready;
    private int videoWidth, videoHeight;

    BackgroundSurface(Context context, Consumer<Boolean> ready) {
        super(context);
        this.ready = ready;
        setClipChildren(true);
        surface = new SurfaceView(context);
        surface.setId(R.id.background_surface);
        surface.setFocusable(false);
        addView(surface, new LayoutParams(-1, -1, Gravity.CENTER));
        surface.getHolder().addCallback(this);
    }
    SurfaceHolder holder() { return surface.getHolder(); }
    void videoSize(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        fit();
    }
    private void fit() {
        if (videoWidth <= 0 || videoHeight <= 0 || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.max(getWidth() / (float) videoWidth, getHeight() / (float) videoHeight);
        surface.setLayoutParams(new LayoutParams(Math.round(videoWidth * scale),
                Math.round(videoHeight * scale), Gravity.CENTER));
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) { fit(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { ready.accept(true); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { ready.accept(false); }
}
