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
    float speed() {
        float speed = store.getFloat("speed", 0.5f);
        return validSpeed(speed) ? speed : 0.5f;
    }
    void setSpeed(float value) {
        if (!validSpeed(value)) throw new IllegalArgumentException("Unsupported playback speed");
        store.edit().putFloat("speed", value).apply();
    }
    static boolean validSpeed(float value) {
        return Float.isFinite(value) && value >= 0.25f && value <= 2f
                && value * 4f == Math.round(value * 4f);
    }
}
