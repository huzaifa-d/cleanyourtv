package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

// Standard-intent resolution adapted from CleanYourTV SystemSettings (MIT).
final class SystemSettings {
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
}
