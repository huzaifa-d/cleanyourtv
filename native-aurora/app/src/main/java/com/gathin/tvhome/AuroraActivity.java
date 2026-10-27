package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.IOException;
import android.content.res.AssetFileDescriptor;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuroraActivity extends Activity {
    private final HomeNavigation navigation = new HomeNavigation();
    private AuroraHomeView home;
    private AppCatalog catalog;
    private AppLibraryDialog library;
    private List<AppCatalog.Entry> apps = new ArrayList<>();
    private boolean launching;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Object> openPanels = new HashSet<>();
    private BackgroundSurface surface;
    private BackgroundSession background;
    private AuroraPreferences preferences;
    private BackgroundFiles files;
    private boolean resumed, windowFocused, surfaceReady, destroyed, defaultVideo, started, screenOn;
    private MemoryCounter memory;
    private AuroraSettingsDialog settings;
    private final ExecutorService memoryWorker = Executors.newSingleThreadExecutor();
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().startsWith("android.intent.action.PACKAGE_")) {
                catalog.invalidate();
                if (library.isShowing()) library.refresh();
                else catalog.load(result -> apps = result);
            } else {
                screenOn = interactive();
                home.updateClock();
                reconcile();
            }
        }
    };
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (!homeActive()) return;
            home.updateClock();
            main.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        catalog = new AppCatalog(this);
        library = new AppLibraryDialog(this, catalog, result -> apps = result);
        if (state != null) navigation.restore(state.getInt("target", HomeNavigation.IDLE),
                state.getInt("last_row", 0));
        home = new AuroraHomeView(this, navigation, this::selected);
        preferences = new AuroraPreferences(getSharedPreferences("aurora", MODE_PRIVATE));
        File directory;
        try {
            File external = getExternalFilesDir(null);
            directory = external == null ? null : new File(external, "backgrounds");
            if (directory != null && !directory.isDirectory() && !directory.mkdirs()) directory = null;
        } catch (RuntimeException unavailable) { directory = null; }
        files = new BackgroundFiles(directory);
        try (AssetFileDescriptor ignored = getAssets().openFd("aurora/blue-bokeh.mp4")) {
            defaultVideo = true;
        } catch (IOException absent) { defaultVideo = false; }
        FrameLayout root = new FrameLayout(this);
        surface = new BackgroundSurface(this, ready -> {
            surfaceReady = ready;
            reconcile();
        });
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        Handler timeout = new Handler(Looper.getMainLooper());
        background = new BackgroundSession(
                () -> new MediaPlayerBackend(this, surface.holder(), surface::videoSize),
                new BackgroundSession.Timer() {
                    @Override public void after(long delay, Runnable action) {
                        timeout.postDelayed(action, delay);
                    }
                    @Override public void cancel() { timeout.removeCallbacksAndMessages(null); }
                }, () -> home.setVideoVisible(false), () -> home.setVideoVisible(true),
                () -> Toast.makeText(this, R.string.video_failed, Toast.LENGTH_LONG).show());
        Handler memoryTimer = new Handler(Looper.getMainLooper());
        memory = new MemoryCounter(new AndroidMemoryReader(this), new MemoryCounter.Scheduler() {
            @Override public void after(long delay, Runnable task) { memoryTimer.postDelayed(task, delay); }
            @Override public void cancel() { memoryTimer.removeCallbacksAndMessages(null); }
        }, memoryWorker, task -> main.post(task), reading -> home.setMemoryText(reading == null
                ? getString(R.string.memory_unavailable) : reading.display()));
        settings = new AuroraSettingsDialog(this, preferences, files, this::beginPanel, this::reconcile);
        setContentView(root);
        home.applyFocus(navigation.target());
        catalog.load(result -> apps = result);
    }
    private void selected(int target) {
        if (target == HomeNavigation.PROFILE) {
            settings.show(() -> { if (!destroyed) home.applyFocus(HomeNavigation.PROFILE); });
            return;
        }
        if (target == BrandShortcut.ALL.ordinal()) {
            if (library.isShowing()) return;
            Runnable dismissed = beginPanel();
            library.show(home, () -> {
                dismissed.run();
                if (!destroyed) home.applyFocus(BrandShortcut.ALL.ordinal());
            });
            return;
        }
        if (launching) return;
        BrandShortcut brand = BrandShortcut.values()[target];
        for (String pkg : brand.packages) {
            AppCatalog.Entry candidate = null;
            for (AppCatalog.Entry entry : apps) {
                if (!pkg.equals(entry.packageName)) continue;
                candidate = entry;
                break;
            }
            if (candidate == null) candidate = catalog.resolve(pkg);
            if (candidate != null) {
                launching = catalog.launch(candidate);
                if (!launching) Toast.makeText(this, getString(R.string.cannot_open, brand.label),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, getString(R.string.not_installed, brand.label), Toast.LENGTH_SHORT).show();
    }
    private boolean interactive() {
        PowerManager power = getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }
    @Override protected void onStart() {
        super.onStart();
        started = true;
        screenOn = interactive();
        IntentFilter system = new IntentFilter();
        system.addAction(Intent.ACTION_TIME_CHANGED);
        system.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        system.addAction(Intent.ACTION_SCREEN_OFF);
        system.addAction(Intent.ACTION_SCREEN_ON);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(changes, system, Context.RECEIVER_EXPORTED);
            registerReceiver(changes, packages, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(changes, system);
            registerReceiver(changes, packages);
        }
        home.updateClock();
    }
    @Override protected void onStop() {
        started = false;
        unregisterReceiver(changes);
        main.removeCallbacks(clockTick);
        super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        screenOn = interactive();
        launching = false;
        if (library.isShowing()) library.refresh();
        else catalog.load(result -> apps = result);
        reconcile();
    }
    @Override protected void onPause() {
        resumed = false;
        reconcile();
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        windowFocused = focused;
        reconcile();
    }
    private boolean homeActive() {
        return !destroyed && resumed && windowFocused && screenOn && openPanels.isEmpty();
    }
    private Runnable beginPanel() {
        Object owner = new Object();
        openPanels.add(owner);
        reconcile();
        return () -> {
            openPanels.remove(owner);
            reconcile();
        };
    }
    private void reconcile() {
        if (background == null || destroyed) return;
        main.removeCallbacks(clockTick);
        home.setMemoryVisible(preferences.showRam());
        memory.setEnabled(preferences.showRam());
        memory.setActive(homeActive());
        if (homeActive()) clockTick.run();
        if (!homeActive() || !surfaceReady) { background.deactivate(); return; }
        String selection = preferences.video();
        File selected = selection.isEmpty() ? null : files.resolve(selection);
        String source = selection.isEmpty() ? defaultVideo ? "asset:blue-bokeh.mp4" : ""
                : selected == null ? "missing:" + selection : selected.getAbsolutePath();
        background.activate(source, preferences.speed());
    }
    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        background.close();
        memory.close();
        memoryWorker.shutdownNow();
        library.close();
        settings.close();
        catalog.close();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("target", navigation.target());
        state.putInt("last_row", navigation.lastRow());
        super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        library.close();
        settings.close();
        reconcile();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (catalog != null && MemoryTrimPolicy.shouldRelease(level, started)) catalog.trim();
    }
    @Override public void onLowMemory() {
        super.onLowMemory();
        if (catalog != null && !started) catalog.trim();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (library != null && library.isShowing()) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (key == KeyEvent.KEYCODE_MENU) {
                home.applyFocus(HomeNavigation.PROFILE);
                selected(HomeNavigation.PROFILE);
                return true;
            }
            HomeNavigation.Direction direction = key == KeyEvent.KEYCODE_DPAD_LEFT
                    ? HomeNavigation.Direction.LEFT : key == KeyEvent.KEYCODE_DPAD_RIGHT
                    ? HomeNavigation.Direction.RIGHT : key == KeyEvent.KEYCODE_DPAD_UP
                    ? HomeNavigation.Direction.UP : key == KeyEvent.KEYCODE_DPAD_DOWN
                    ? HomeNavigation.Direction.DOWN : null;
            if (direction != null) {
                home.applyFocus(navigation.move(direction));
                return true;
            }
            if (key == KeyEvent.KEYCODE_BACK) {
                navigation.clear();
                home.applyFocus(HomeNavigation.IDLE);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
