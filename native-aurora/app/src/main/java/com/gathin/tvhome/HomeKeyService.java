package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

public final class HomeKeyService extends AccessibilityService {
    private static HomeKeyService connected;
    private final HomeKeyHandler keys = new HomeKeyHandler(this::openHome);

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        StandbyScheduler.reschedule(this);
    }

    @Override public void onDestroy() {
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
        return keys.handle(event.getKeyCode(), event.getAction(), event.getRepeatCount());
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
    @Override public void onInterrupt() { keys.reset(); }
}
