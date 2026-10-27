package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.util.function.BiConsumer;

final class MediaPlayerBackend implements BackgroundSession.Player {
    private final Context context;
    private final SurfaceHolder holder;
    private final BiConsumer<Integer, Integer> sizeChanged;
    private MediaPlayer player;
    private float speed;

    MediaPlayerBackend(Context context, SurfaceHolder holder, BiConsumer<Integer, Integer> sizeChanged) {
        this.context = context.getApplicationContext();
        this.holder = holder;
        this.sizeChanged = sizeChanged;
    }
    @Override public void prepare(String source, float speed, BackgroundSession.Events events) {
        if (source.startsWith("missing:")) throw new IllegalStateException("Background file is unavailable");
        this.speed = speed;
        player = new MediaPlayer();
        player.setOnPreparedListener(ignored -> events.prepared());
        player.setOnErrorListener((ignored, what, extra) -> { events.failed(); return true; });
        player.setOnVideoSizeChangedListener((ignored, width, height) -> sizeChanged.accept(width, height));
        player.setVolume(0f, 0f);
        player.setLooping(true);
        player.setDisplay(holder);
        try {
            if (source.equals("asset:blue-bokeh.mp4")) {
                try (AssetFileDescriptor asset = context.getAssets().openFd("aurora/blue-bokeh.mp4")) {
                    player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
                }
            } else player.setDataSource(source);
            player.prepareAsync();
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override public void start() {
        player.setPlaybackParams(new PlaybackParams()
                .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_FAIL).setSpeed(speed));
        player.start();
    }
    @Override public void release() {
        if (player == null) return;
        MediaPlayer old = player;
        player = null;
        old.setOnPreparedListener(null);
        old.setOnErrorListener(null);
        old.setOnVideoSizeChangedListener(null);
        old.release();
    }
}
