package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// Catalogue and guarded icon loading adapted from CleanYourTV AppLibraryDialog (MIT).
final class AppCatalog {
    static final class Entry {
        final String packageName, label;
        final ComponentName component;
        Entry(String packageName, String label, ComponentName component) {
            this.packageName = packageName;
            this.label = label;
            this.component = component;
        }
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final LibraryLoads loads = new LibraryLoads();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private volatile boolean closed;
    private volatile int iconGeneration;

    AppCatalog(Context context) { this.context = context.getApplicationContext(); }

    static <T> void preferFirst(Map<String, T> merged, String self, String pkg, T candidate) {
        if (!self.equals(pkg)) merged.putIfAbsent(pkg, candidate);
    }

    void load(Consumer<List<Entry>> callback) {
        if (closed) return;
        int generation = loads.start(false);
        loader.execute(() -> {
            PackageManager pm = context.getPackageManager();
            Map<String, ResolveInfo> unique = new LinkedHashMap<>();
            for (String category : new String[] {
                    Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
                List<ResolveInfo> resolvedApps;
                try { resolvedApps = pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(category), 0); }
                catch (RuntimeException unavailable) { continue; }
                for (ResolveInfo resolved : resolvedApps) {
                    if (resolved.activityInfo != null && resolved.activityInfo.exported) {
                        preferFirst(unique, context.getPackageName(),
                                resolved.activityInfo.packageName, resolved);
                    }
                }
            }
            List<Entry> result = new ArrayList<>();
            for (Map.Entry<String, ResolveInfo> candidate : unique.entrySet()) {
                ResolveInfo resolved = candidate.getValue();
                String label = candidate.getKey();
                try {
                    CharSequence loaded = resolved.loadLabel(pm);
                    if (loaded != null && !loaded.toString().trim().isEmpty()) label = loaded.toString();
                } catch (RuntimeException unavailable) { /* Package may be updating. */ }
                result.add(new Entry(candidate.getKey(), label, new ComponentName(
                        candidate.getKey(), resolved.activityInfo.name)));
            }
            Collator order = Collator.getInstance();
            result.sort((a, b) -> {
                int label = order.compare(a.label, b.label);
                return label != 0 ? label : a.packageName.compareTo(b.packageName);
            });
            main.post(() -> {
                if (!closed && loads.isLatest(generation)) callback.accept(result);
            });
        });
    }

    void icon(Entry entry, int size, Consumer<Bitmap> callback) {
        if (closed) return;
        String key = entry.packageName + ":" + size;
        Bitmap cached = icons.get(key);
        if (cached != null) { callback.accept(cached); return; }
        int generation = iconGeneration;
        loader.execute(() -> {
            if (closed || generation != iconGeneration) return;
            Bitmap bitmap = null;
            try {
                Drawable drawable = context.getPackageManager().getActivityIcon(entry.component);
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, size, size);
                drawable.draw(new Canvas(bitmap));
            } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
                bitmap = null;
            }
            Bitmap loaded = bitmap;
            main.post(() -> {
                if (closed || generation != iconGeneration) return;
                if (loaded != null) icons.put(key, loaded);
                callback.accept(loaded);
            });
        });
    }

    void invalidate() {
        loads.start(false);
        iconGeneration++;
        icons.evictAll();
    }
    void trim() { iconGeneration++; icons.evictAll(); }
    Entry resolve(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            ComponentName component = intent == null ? null : intent.getComponent();
            return component == null ? null : new Entry(pkg, pkg, component);
        } catch (RuntimeException unavailable) { return null; }
    }
    boolean launch(Entry entry) {
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
    void close() {
        closed = true;
        loader.shutdownNow();
        main.removeCallbacksAndMessages(null);
        icons.evictAll();
    }
}
