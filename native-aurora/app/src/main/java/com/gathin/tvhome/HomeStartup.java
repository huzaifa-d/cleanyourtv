package com.gathin.tvhome;

final class HomeStartup {
    private final boolean enabled;
    private boolean screenOn, pending;
    private int handledBoot;

    HomeStartup(boolean enabled, boolean screenOn, int handledBoot) {
        this.enabled = enabled;
        this.screenOn = screenOn;
        this.handledBoot = handledBoot;
    }

    boolean boot(int boot) {
        if (!enabled || boot < 0 || boot == handledBoot) return false;
        handledBoot = boot;
        pending = screenOn;
        return true;
    }

    int handledBoot() { return handledBoot; }
    boolean pending() { return pending; }

    void screen(boolean on) {
        if (screenOn == on) return;
        screenOn = on;
        pending = enabled && on;
    }

    void cancel() { pending = false; }

    boolean takeLaunch(boolean interactive, boolean unlocked) {
        if (!interactive) pending = false;
        if (!enabled || !pending || !unlocked) return false;
        pending = false;
        return true;
    }
}
