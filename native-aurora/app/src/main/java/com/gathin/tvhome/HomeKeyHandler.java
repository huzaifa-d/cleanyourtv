package com.gathin.tvhome;

import android.view.KeyEvent;
import java.util.function.BooleanSupplier;

final class HomeKeyHandler {
    private final BooleanSupplier launch;
    private boolean consumed;

    HomeKeyHandler(BooleanSupplier launch) { this.launch = launch; }

    boolean handle(int key, int action, int repeat) {
        if (key != KeyEvent.KEYCODE_HOME) return false;
        if (action == KeyEvent.ACTION_DOWN) {
            if (repeat == 0) consumed = launch.getAsBoolean();
            return consumed;
        }
        if (action != KeyEvent.ACTION_UP) return false;
        boolean handled = consumed;
        reset();
        return handled;
    }

    void reset() { consumed = false; }
}
