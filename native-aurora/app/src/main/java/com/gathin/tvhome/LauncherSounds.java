package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class LauncherSounds implements AutoCloseable {
    private static final String TAG = "AuroraSounds";
    private final Context context;
    private final SoundSession session;
    private final SoundPool pool;
    private final Map<View, FocusHooks> roots = new IdentityHashMap<>();
    private final Map<View, Boolean> muted = new WeakHashMap<>();
    private int loginSample, navigationSample, loginStream, navigationStream;
    private boolean closed;

    LauncherSounds(Context context, File directory, boolean loginPlayed) {
        this.context = context;
        File login = directory == null ? null : new File(directory, "Login.mp3");
        File navigation = directory == null ? null : new File(directory, "Nav.mp3");
        boolean configured = (login != null && login.exists()) || (navigation != null && navigation.exists());
        pool = configured ? new SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build() : null;
        session = new SoundSession(new SoundSession.Backend() {
            @Override public void login() { loginStream = play(loginSample, loginStream, true); }
            @Override public void navigation() {
                navigationStream = play(navigationSample, navigationStream, false);
            }
            @Override public void stop() {
                if (pool == null) return;
                if (loginStream != 0) pool.stop(loginStream);
                if (navigationStream != 0) pool.stop(navigationStream);
                loginStream = navigationStream = 0;
            }
        }, loginPlayed);
        if (pool != null) {
            pool.setOnLoadCompleteListener((owner, sample, status) -> {
                if (closed) return;
                boolean isLogin = sample == loginSample;
                if (status != 0) {
                    failed(isLogin, new IllegalStateException("Sound decoder status " + status));
                    return;
                }
                session.loaded(isLogin);
                if (!isLogin) for (View root : roots.keySet()) mute(root);
                Log.i(TAG, isLogin ? "Custom login sound loaded" : "Custom navigation sound loaded");
            });
            loginSample = load(login, true);
            navigationSample = load(navigation, false);
        }
    }

    private int load(File file, boolean login) {
        if (file == null || !file.exists()) return 0;
        try {
            int sample = pool.load(file.getAbsolutePath(), 1);
            if (sample == 0) failed(login, new IllegalStateException("Sound could not be queued"));
            return sample;
        } catch (IllegalArgumentException | SecurityException failure) {
            failed(login, failure);
            return 0;
        }
    }

    private int play(int sample, int previous, boolean login) {
        if (previous != 0) pool.stop(previous);
        int stream = pool.play(sample, 1f, 1f, 1, 0, 1f);
        if (stream == 0) failed(login, new IllegalStateException("Sound stream could not start"));
        return stream;
    }

    private void failed(boolean login, RuntimeException failure) {
        Log.w(TAG, login ? "Custom login sound failed" : "Custom navigation sound failed", failure);
        Toast.makeText(context, context.getString(R.string.custom_sound_failed,
                login ? "Login.mp3" : "Nav.mp3"), Toast.LENGTH_LONG).show();
        if (!login) {
            session.navigationFailed();
            restoreEffects();
        }
    }

    void setActive(boolean active) { session.setActive(active); }
    boolean loginPlayed() { return session.loginPlayed(); }

    void attach(View root) {
        if (navigationSample == 0 || roots.containsKey(root) || closed) return;
        FocusHooks hooks = new FocusHooks(root);
        roots.put(root, hooks);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(hooks);
        root.addOnAttachStateChangeListener(hooks);
        if (session.hasNavigation()) mute(root);
    }

    void attach(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) throw new IllegalStateException("Dialog has no sound window");
        attach(window.getDecorView());
        dialog.setOnKeyListener((owner, key, event) -> {
            if (!session.hasNavigation() || !event.hasNoModifiers()) return false;
            final int direction;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
                case KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
                default: return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) return true;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (window.superDispatchKeyEvent(event)) return true;
            View root = window.getDecorView(), focused = root.findFocus();
            if (focused == null) {
                root.restoreDefaultFocus();
                return true;
            }
            View next = focused.focusSearch(direction);
            if (next != null && next != focused) {
                Rect rectangle = new Rect();
                focused.getFocusedRect(rectangle);
                if (root instanceof ViewGroup) {
                    ((ViewGroup) root).offsetDescendantRectToMyCoords(focused, rectangle);
                    ((ViewGroup) root).offsetRectIntoDescendantCoords(next, rectangle);
                }
                if (next.requestFocus(direction, rectangle)) return true;
            }
            // Handle unconsumed focus navigation here, before ViewRootImpl adds its own sound.
            root.dispatchUnhandledMove(focused, direction);
            return true;
        });
    }

    private void mute(View view) {
        if (!muted.containsKey(view)) muted.put(view, view.isSoundEffectsEnabled());
        view.setSoundEffectsEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) mute(group.getChildAt(i));
        }
    }

    private void restoreEffects() {
        for (Map.Entry<View, Boolean> entry : muted.entrySet()) {
            entry.getKey().setSoundEffectsEnabled(entry.getValue());
        }
        muted.clear();
    }

    private final class FocusHooks implements ViewTreeObserver.OnGlobalFocusChangeListener,
            View.OnAttachStateChangeListener {
        private final View root;
        FocusHooks(View root) { this.root = root; }
        @Override public void onGlobalFocusChanged(View previous, View next) {
            if (session.hasNavigation() && next != null) mute(next);
            session.focusChanged(previous, next);
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            if (root.getViewTreeObserver().isAlive()) {
                root.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
            }
            root.removeOnAttachStateChangeListener(this);
            roots.remove(root);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        session.close();
        for (FocusHooks hooks : new ArrayList<>(roots.values())) {
            hooks.onViewDetachedFromWindow(hooks.root);
        }
        restoreEffects();
        if (pool != null) {
            pool.setOnLoadCompleteListener(null);
            pool.release();
        }
    }
}
