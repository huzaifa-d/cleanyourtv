package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

public final class HomeKeyService extends AccessibilityService {
    private static HomeKeyService connected;
    private final HomeKeyHandler keys = new HomeKeyHandler(this::openHome);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable startupLaunch = this::openAtStartup;
    private HomeStartup startup;
    private BroadcastReceiver screenReceiver;
    private static final long STARTUP_DELAY_MS = 800;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        StandbyScheduler.reschedule(this);
        configureStartup();
    }

    @Override public void onDestroy() {
        stopStartup();
        if (connected == this) connected = null;
        super.onDestroy();
    }

    static boolean isConnected() { return connected != null; }

    static boolean standby() {
        if (connected == null || android.os.Build.VERSION.SDK_INT < 28) return false;
        try {
            return connected.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (SecurityException failure) {
            Log.e("CherryHome", "Could not request standby", failure);
            return false;
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (startup != null && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() != KeyEvent.KEYCODE_POWER
                && event.getKeyCode() != KeyEvent.KEYCODE_WAKEUP
                && event.getKeyCode() != KeyEvent.KEYCODE_SLEEP
                && event.getKeyCode() != KeyEvent.KEYCODE_SOFT_SLEEP) {
            startup.cancel();
            handler.removeCallbacks(startupLaunch);
        }
        return keys.handle(event.getKeyCode(), event.getAction(), event.getRepeatCount());
    }

    private static SharedPreferences startupStore(Context context) {
        return context.getSharedPreferences("startup_home", MODE_PRIVATE);
    }

    static boolean startupEnabled(Context context) {
        return startupStore(context).getBoolean("enabled", false);
    }

    static boolean setStartupEnabled(Context context, boolean enabled) {
        // Enabling or updating the app is not a new power-on.
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (!startupStore(context).edit().putBoolean("enabled", enabled).putInt("handled_boot", boot).commit()) {
            Log.e("CherryHome", "Could not save power-on Home setting");
            Toast.makeText(context, R.string.startup_home_save_failed, Toast.LENGTH_LONG).show();
            return false;
        }
        if (connected != null) connected.configureStartup();
        return true;
    }

    private boolean interactive() {
        PowerManager power = getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }

    private void configureStartup() {
        stopStartup();
        if (!startupEnabled(this)) return;
        SharedPreferences store = startupStore(this);
        startup = new HomeStartup(true, interactive(), store.getInt("handled_boot", -1));
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (startup == null) return;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    startup.screen(false);
                    handler.removeCallbacks(startupLaunch);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    startup.screen(true);
                    queueStartup();
                } else {
                    queueStartup();
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            screenReceiver = receiver;
        } catch (IllegalArgumentException | SecurityException failure) {
            Log.e("CherryHome", "Could not observe power-on events", failure);
            startup = null;
            Toast.makeText(this, R.string.startup_home_failed, Toast.LENGTH_LONG).show();
            return;
        }
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) {
            Log.e("CherryHome", "Boot count unavailable; automatic restart handover is not ready");
            Toast.makeText(this, R.string.startup_home_failed, Toast.LENGTH_LONG).show();
        } else if (startup.boot(boot)) {
            if (!store.edit().putInt("handled_boot", startup.handledBoot()).commit()) {
                startup.cancel();
                Log.e("CherryHome", "Could not record boot; cancelling automatic Home launch");
                Toast.makeText(this, R.string.startup_home_save_failed, Toast.LENGTH_LONG).show();
            }
            queueStartup();
        }
        Log.i("CherryHome", "Power-on Home enabled; observing restart and screen wake");
    }

    private void queueStartup() {
        if (startup == null || !startup.pending()) return;
        handler.removeCallbacks(startupLaunch);
        handler.postDelayed(startupLaunch, STARTUP_DELAY_MS);
    }

    private void openAtStartup() {
        if (startup == null) return;
        UserManager users = getSystemService(UserManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        boolean unlocked = users != null && users.isUserUnlocked()
                && keyguard != null && !keyguard.isKeyguardLocked();
        if (startup.takeLaunch(interactive(), unlocked) && openHome()) {
            Log.i("CherryHome", "Opened Cherry for power-on Home");
        }
    }

    private void stopStartup() {
        handler.removeCallbacks(startupLaunch);
        if (startup != null) startup.cancel();
        startup = null;
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
            screenReceiver = null;
        }
    }

    private boolean openHome() {
        try {
            startActivity(new Intent(this, AuroraActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.e("CherryHome", "Could not open Cherry Launcher; leaving Home to Android", failure);
            Toast.makeText(this, R.string.home_button_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    static boolean isEnabled(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        ComponentName own = new ComponentName(context, HomeKeyService.class);
        for (AccessibilityServiceInfo service :
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (own.equals(ComponentName.unflattenFromString(service.getId()))) return true;
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() {
        keys.reset();
        if (startup != null) startup.cancel();
        handler.removeCallbacks(startupLaunch);
    }
}
