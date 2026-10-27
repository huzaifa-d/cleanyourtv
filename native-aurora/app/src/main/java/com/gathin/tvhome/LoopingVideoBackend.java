package com.gathin.tvhome;

import android.content.Context;
import android.net.Uri;
import android.view.SurfaceHolder;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.File;
import java.util.Arrays;
import java.util.function.BiConsumer;

@OptIn(markerClass = UnstableApi.class)
final class LoopingVideoBackend implements BackgroundSession.Player {
    private final Context context;
    private final SurfaceHolder holder;
    private final BiConsumer<Integer, Integer> sizeChanged;
    private ExoPlayer player;

    LoopingVideoBackend(Context context, SurfaceHolder holder, BiConsumer<Integer, Integer> sizeChanged) {
        this.context = context.getApplicationContext();
        this.holder = holder;
        this.sizeChanged = sizeChanged;
    }
    @Override public void prepare(String source, BackgroundSession.Events events) {
        if (source.startsWith("missing:")) throw new IllegalStateException("Background file is unavailable");
        ExoPlayer current = new ExoPlayer.Builder(context)
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(2000, 5000, 1500, 1000)
                        .setTargetBufferBytes(4 * 1024 * 1024)
                        .build())
                .build();
        player = current;
        current.addListener(new Player.Listener() {
            private boolean prepared;
            @Override public void onPlaybackStateChanged(int state) {
                if (player != current || prepared || state != Player.STATE_READY) return;
                prepared = true;
                events.prepared();
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (player == current) events.failed();
            }
            @Override public void onVideoSizeChanged(VideoSize size) {
                if (player == current) sizeChanged.accept(size.width, size.height);
            }
        });
        current.setVolume(0f);
        current.setTrackSelectionParameters(current.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
        current.setVideoSurfaceHolder(holder);
        current.setPlaybackSpeed(1f);
        Uri uri = source.equals("asset:blue-bokeh.mp4")
                ? Uri.parse("asset:///aurora/blue-bokeh.mp4") : Uri.fromFile(new File(source));
        MediaItem item = MediaItem.fromUri(uri);
        // Distinct playlist entries queue the next loop without seeking/flushing the decoder.
        current.setMediaItems(Arrays.asList(item, item));
        current.setRepeatMode(Player.REPEAT_MODE_ALL);
        current.prepare();
    }
    @Override public void start() {
        player.play();
    }
    @Override public void release() {
        if (player == null) return;
        ExoPlayer old = player;
        player = null;
        old.release();
    }
}
