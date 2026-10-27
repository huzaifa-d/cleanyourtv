package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import java.util.ArrayList;
import java.util.List;

// Input discovery and channel intents adapted from CleanYourTV MainActivity (MIT).
final class InputsController {
    static final String[] SONY_TV_PACKAGES = {"com.sony.dtv.tvx", "com.sony.dtv.tvlin"};
    static final class Entry {
        final String label;
        final Intent intent;
        Entry(String label, Intent intent) { this.label = label; this.intent = intent; }
    }
    private final Activity activity;
    InputsController(Activity activity) { this.activity = activity; }
    static Intent passthroughIntent(String id) {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUriForPassthroughInput(id),
                        TvContract.Channels.CONTENT_ITEM_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    static Intent tunerIntent(String id) {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelsUriForInput(id), TvContract.Channels.CONTENT_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        Intent liveTv = null;
        try {
            TvInputManager manager = activity.getSystemService(TvInputManager.class);
            if (manager != null) {
                for (TvInputInfo info : manager.getTvInputList()) {
                    if (info.isHidden(activity)) continue;
                    if (info.isPassthroughInput()) {
                        CharSequence custom = info.loadCustomLabel(activity);
                        CharSequence label = custom == null ? info.loadLabel(activity) : custom;
                        entries.add(new Entry(label == null ? info.getId() : label.toString(),
                                preferSony(passthroughIntent(info.getId()))));
                    } else if (liveTv == null) {
                        Intent candidate = preferSony(tunerIntent(info.getId()));
                        if (candidate.resolveActivity(activity.getPackageManager()) != null) liveTv = candidate;
                    }
                }
            }
        } catch (RuntimeException unavailable) { /* Offer Sony Live TV and system settings instead. */ }
        entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        if (liveTv == null) {
            for (String pkg : SONY_TV_PACKAGES) {
                try {
                    Intent intent = activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (intent == null) intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (intent != null) { liveTv = intent; break; }
                } catch (RuntimeException unavailable) { /* Another Sony entry may still be available. */ }
            }
        }
        if (liveTv != null) entries.add(new Entry(activity.getString(R.string.live_tv), liveTv));
        return entries;
    }
    private Intent preferSony(Intent generic) {
        for (String pkg : SONY_TV_PACKAGES) {
            Intent sony = new Intent(generic).setPackage(pkg);
            if (sony.resolveActivity(activity.getPackageManager()) != null) return sony;
        }
        return generic;
    }
    boolean open(Entry entry) {
        try {
            activity.startActivity(entry.intent);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
