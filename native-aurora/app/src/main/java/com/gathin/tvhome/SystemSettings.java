package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.util.Log;

// Standard-intent resolution adapted from CleanYourTV SystemSettings (MIT).
final class SystemSettings {
    static final String ACTION_QUICK_SETTINGS = "android.app.action.OPEN_NOTIFICATION_HANDLER_PANEL";
    private SystemSettings() { }
    static Intent intent(Activity activity, String action) {
        Intent intent = new Intent(action);
        ResolveInfo resolved = activity.getPackageManager().resolveActivity(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && resolved.activityInfo.exported
                && !resolved.activityInfo.name.contains("CTSDummy") ? intent : null;
    }
    static boolean open(Activity activity, boolean applications) {
        try {
            Intent intent = intent(activity, applications
                    ? Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS : Settings.ACTION_SETTINGS);
            if (intent == null && applications) intent = intent(activity, Settings.ACTION_SETTINGS);
            if (intent == null) return false;
            activity.startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
    static boolean openQuickSettings(Activity activity) {
        return openAction(activity, ACTION_QUICK_SETTINGS);
    }
    static boolean openAccessibility(Activity activity) {
        return openAction(activity, Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }
    static boolean openExactAlarmAccess(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT < 31) return false;
        Intent target = intent(activity, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        if (target == null) {
            Log.w("CherrySettings", "Exact-alarm access settings are unavailable");
            return false;
        }
        target.setData(android.net.Uri.fromParts("package", activity.getPackageName(), null));
        try {
            activity.startActivity(target);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException failure) {
            Log.w("CherrySettings", "Could not open exact-alarm access settings", failure);
            return false;
        }
    }
    static boolean openProfiles(Activity activity) {
        return openComponent(activity, new ComponentName(
                "com.google.android.apps.tv.launcherx",
                "com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity"));
    }
    static boolean openGoogleHome(Activity activity) {
        try {
            if (!activity.getPackageManager().getApplicationInfo(
                    "com.google.android.apps.tv.launcherx", 0).enabled) return false;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
        return openComponent(activity, new ComponentName("com.google.android.apps.tv.dreamx",
                "com.google.android.libraries.tv.smarthome.core.SmartHomeMainActivity"));
    }
    private static boolean openComponent(Activity activity, ComponentName component) {
        Intent target = new Intent().setComponent(component);
        try {
            android.content.pm.ActivityInfo info = activity.getPackageManager()
                    .getActivityInfo(component, 0);
            if (!info.exported || !info.enabled || !info.applicationInfo.enabled) return false;
            if (info.permission != null && activity.checkSelfPermission(info.permission)
                    != PackageManager.PERMISSION_GRANTED) return false;
            activity.startActivity(target);
            return true;
        } catch (PackageManager.NameNotFoundException | android.content.ActivityNotFoundException
                | SecurityException unavailable) {
            Log.w("CherrySettings", "Native Google TV page unavailable: " + component, unavailable);
            return false;
        }
    }
    static boolean openAction(Activity activity, String action) {
        Intent target = intent(activity, action);
        if (target == null) {
            Log.w("CherrySettings", "No exported activity handles " + action);
            return false;
        }
        try {
            activity.startActivity(target);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException failure) {
            Log.w("CherrySettings", "Could not open " + action, failure);
            return false;
        }
    }
}
