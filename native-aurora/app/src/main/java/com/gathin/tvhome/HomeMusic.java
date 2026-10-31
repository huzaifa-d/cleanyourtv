package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

final class HomeMusic implements AutoCloseable {
    private static final String TAG = "CherryMusic";
    private final Context context;
    private final File file;
    private final AudioManager audio;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final HomeMusicSession session;
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private long generation;

    HomeMusic(Context context, File directory) {
        this.context = context;
        file = directory == null ? null : new File(directory, "Menu.mp3");
        audio = context.getSystemService(AudioManager.class);
        session = new HomeMusicSession(new HomeMusicSession.Backend() {
            @Override public void prepare(long generation) { HomeMusic.this.prepare(generation); }
            @Override public void start() { HomeMusic.this.start(); }
            @Override public void release() { HomeMusic.this.release(); }
        });
    }

    private void prepare(long generation) {
        this.generation = generation;
        try {
            if (file == null || !file.exists()) {
                session.finished(generation);
                return;
            }
            MediaPlayer loading = new MediaPlayer();
            player = loading;
            loading.setAudioAttributes(attributes);
            loading.setLooping(false);
            loading.setOnPreparedListener(owner -> {
                if (player == owner) session.prepared(generation);
            });
            loading.setOnCompletionListener(owner -> {
                if (player == owner) {
                    Log.i(TAG, "Menu.mp3 finished; waiting for the next Home return");
                    session.finished(generation);
                }
            });
            loading.setOnErrorListener((owner, what, extra) -> {
                if (player == owner) failed(generation,
                        new IllegalStateException("Music decoder error " + what + "/" + extra));
                return true;
            });
            loading.setDataSource(file.getAbsolutePath());
            loading.prepareAsync();
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException failure) {
            failed(generation, failure);
        }
    }

    private void start() {
        long currentGeneration = generation;
        MediaPlayer ready = player;
        if (audio == null) {
            failed(currentGeneration, new IllegalStateException("Audio manager is unavailable"));
            return;
        }
        try {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        if (player != ready) return;
                        if (change == AudioManager.AUDIOFOCUS_LOSS
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            Log.i(TAG, "Home music stopped for another audio owner");
                            session.finished(currentGeneration);
                        }
                    }, new Handler(Looper.getMainLooper())).build();
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Home music could not obtain audio focus; not playing over another app");
                session.finished(currentGeneration);
                return;
            }
            ready.start();
            Log.i(TAG, "Menu.mp3 started from the beginning on Home");
        } catch (IllegalStateException | IllegalArgumentException | SecurityException failure) {
            failed(currentGeneration, failure);
        }
    }

    private void failed(long generation, Exception failure) {
        Log.w(TAG, "Custom Home music failed", failure);
        session.finished(generation);
        Toast.makeText(context, context.getString(R.string.custom_sound_failed, "Menu.mp3"),
                Toast.LENGTH_LONG).show();
    }

    private void release() {
        MediaPlayer previous = player;
        player = null;
        if (previous != null) {
            previous.setOnPreparedListener(null);
            previous.setOnCompletionListener(null);
            previous.setOnErrorListener(null);
            previous.release();
        }
        if (focus != null && audio != null) {
            audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    void setActive(boolean active) { session.setActive(active); }
    @Override public void close() { session.close(); }
}
