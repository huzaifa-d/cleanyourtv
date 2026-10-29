package com.gathin.tvhome;

final class SoundSession {
    interface Backend {
        void login();
        void navigation();
        void stop();
    }

    private final Backend backend;
    private boolean active, closed, loginReady, navigationReady, loginPlayed;

    SoundSession(Backend backend, boolean loginPlayed) {
        this.backend = backend;
        this.loginPlayed = loginPlayed;
    }

    void loaded(boolean login) {
        if (closed) return;
        if (login) loginReady = true;
        else navigationReady = true;
        startLogin();
    }

    void setActive(boolean active) {
        if (closed || this.active == active) return;
        this.active = active;
        if (active) startLogin();
        else backend.stop();
    }

    private void startLogin() {
        if (!active || !loginReady || loginPlayed) return;
        loginPlayed = true;
        backend.login();
    }

    boolean loginPlayed() { return loginPlayed; }
    boolean hasNavigation() { return !closed && navigationReady; }
    void navigationFailed() { navigationReady = false; }

    void focusChanged(Object previous, Object next) {
        if (active && hasNavigation() && previous != null && next != null && previous != next) {
            backend.navigation();
        }
    }

    void close() {
        if (closed) return;
        setActive(false);
        closed = true;
    }
}
