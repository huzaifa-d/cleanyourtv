package com.gathin.tvhome;

import android.content.SharedPreferences;

final class AuroraPreferences {
    private final SharedPreferences store;
    AuroraPreferences(SharedPreferences store) { this.store = store; }
    boolean showRam() { return store.getBoolean("show_ram", true); }
    void setShowRam(boolean enabled) { store.edit().putBoolean("show_ram", enabled).apply(); }
    String video() {
        String name = store.getString("video", "");
        return name != null && (name.isEmpty() || BackgroundFiles.validName(name)) ? name : "";
    }
    void setVideo(String filename) {
        if (filename == null || (!filename.isEmpty() && !BackgroundFiles.validName(filename))) {
            throw new IllegalArgumentException("Expected a direct-child MP4 filename");
        }
        store.edit().putString("video", filename).apply();
    }
}
