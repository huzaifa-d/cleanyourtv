package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Consumer;

final class AuroraSettingsDialog {
    private final Activity activity;
    private final AuroraPreferences preferences;
    private final BackgroundFiles files;
    private final Supplier<Runnable> beginPanel;
    private final Runnable changed;
    private final Consumer<AlertDialog> attachSounds;
    private final Set<AlertDialog> panels = new LinkedHashSet<>();
    private AlertDialog hub;
    private CherryDrawer drawer;
    private View mainContent, returnFocus;
    private int mainScroll;
    AuroraSettingsDialog(Activity activity, AuroraPreferences preferences, BackgroundFiles files,
            Supplier<Runnable> beginPanel, Runnable changed, Consumer<AlertDialog> attachSounds) {
        this.activity = activity;
        this.preferences = preferences;
        this.files = files;
        this.beginPanel = beginPanel;
        this.changed = changed;
        this.attachSounds = attachSounds;
    }
    boolean isShowing() { return hub != null && hub.isShowing(); }
    void show(Runnable dismissed) {
        if (isShowing()) return;
        drawer = new CherryDrawer(activity, () -> system(false), () -> {
            if (SystemSettings.openProfiles(activity)) close();
            else message(R.string.profile_unavailable);
        });
        List<View> tiles = new ArrayList<>();
        tiles.add(drawer.button(R.string.background, DrawerIcon.BACKGROUND, this::background));
        Switch ram = new Switch(activity);
        drawer.style(ram, DrawerIcon.RAM);
        ram.setId(R.id.ram_switch);
        ram.setText(R.string.drawer_ram);
        ram.setShowText(false);
        ram.setSwitchMinWidth(drawer.u(20));
        ram.setSwitchPadding(drawer.u(4));
        ShapeDrawable thumb = new ShapeDrawable(new Shape() {
            @Override public void draw(Canvas canvas, Paint paint) {
                canvas.drawCircle(getWidth() / 2, getHeight() / 2,
                        Math.min(getWidth(), getHeight()) / 2, paint);
            }
        });
        thumb.setIntrinsicWidth(drawer.u(10));
        thumb.setIntrinsicHeight(drawer.u(14));
        thumb.getPaint().setColor(0xfff3c8d2);
        ram.setThumbDrawable(thumb);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(drawer.u(7));
        track.setSize(drawer.u(20), drawer.u(14));
        track.setColor(0xffb65c76);
        ram.setTrackDrawable(track);
        ram.setTrackTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {0xffb65c76, 0xff541526}));
        ram.setChecked(preferences.showRam());
        ram.setOnCheckedChangeListener((view, enabled) -> {
            preferences.setShowRam(enabled);
            changed.run();
        });
        tiles.add(ram);
        tiles.add(drawer.button(R.string.screensaver, DrawerIcon.SCREENSAVER,
                () -> shortcut(R.string.screensaver, Settings.ACTION_DREAM_SETTINGS)));
        tiles.add(drawer.button(R.string.inputs, DrawerIcon.INPUTS, this::inputs));
        tiles.add(drawer.button(R.string.bluetooth, DrawerIcon.BLUETOOTH,
                () -> shortcut(R.string.bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS)));
        tiles.add(drawer.button(R.string.accessibility, DrawerIcon.ACCESSIBILITY,
                () -> shortcut(R.string.accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        tiles.add(drawer.button(R.string.app_management, DrawerIcon.APPS, () -> system(true)));
        tiles.add(drawer.button(R.string.home_button_setup, DrawerIcon.HOME, this::homeButton));
        tiles.add(drawer.button(R.string.google_home, DrawerIcon.GOOGLE_HOME, () -> {
            if (SystemSettings.openGoogleHome(activity)) close();
            else message(R.string.google_home_unavailable);
        }));
        tiles.add(drawer.button(R.string.daily_standby, DrawerIcon.SCREENSAVER, this::dailyStandby));
        mainContent = drawer.grid(tiles);
        returnFocus = tiles.get(0);
        mainScroll = 0;
        drawer.main(mainContent, returnFocus, mainScroll);
        hub = new AlertDialog(activity) {
            @Override public void onBackPressed() {
                if (!drawer.back()) super.onBackPressed();
            }
        };
        hub.setView(drawer, 0, 0, 0, 0);
        showPanel(hub, () -> { hub = null; drawer = null; mainContent = null; returnFocus = null; dismissed.run(); });
        Window window = hub.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN);
            WindowManager.LayoutParams layout = window.getAttributes();
            layout.gravity = Gravity.TOP | Gravity.RIGHT;
            layout.x = drawer.u(16);
            layout.y = drawer.u(16);
            layout.width = drawer.u(300);
            layout.height = activity.getResources().getDisplayMetrics().heightPixels - drawer.u(32);
            layout.dimAmount = .5f;
            window.setAttributes(layout);
        }
        returnFocus.requestFocus();
    }
    private void background() {
        LinearLayout rows = rows();
        populateBackground(rows);
        page(R.string.background, rows);
        rows.getChildAt(1).requestFocus();
    }
    private void populateBackground(LinearLayout rows) {
        rows.removeAllViews();
        File directory = files.directory();
        TextView location = new TextView(activity);
        location.setText(activity.getString(R.string.background_directory,
                directory == null ? activity.getString(R.string.storage_unavailable) : directory.getAbsolutePath(),
                preferences.video().isEmpty() ? activity.getString(R.string.default_background) : preferences.video()));
        drawer.text(location, 12);
        location.setPadding(0, drawer.u(8), 0, drawer.u(8));
        rows.addView(location);
        button(rows, R.string.refresh, () -> { populateBackground(rows); rows.getChildAt(1).requestFocus(); });
        button(rows, R.string.default_background, () -> {
            preferences.setVideo("");
            changed.run();
            populateBackground(rows);
            rows.getChildAt(2).requestFocus();
        });
        List<File> available = files.list();
        if (available.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.no_background_files);
            drawer.text(empty, 12);
            empty.setPadding(0, drawer.u(12), 0, drawer.u(12));
            rows.addView(empty);
        }
        for (File file : available) {
            Button item = drawer.button(file.getName(), -1, () -> { });
            item.setOnClickListener(v -> {
                if (files.resolve(file.getName()) == null) { message(R.string.file_missing); return; }
                preferences.setVideo(file.getName());
                changed.run();
                int selectedIndex = rows.indexOfChild(item);
                populateBackground(rows);
                rows.getChildAt(Math.min(selectedIndex, rows.getChildCount() - 1)).requestFocus();
            });
            rows.addView(item);
        }
    }
    private void inputs() {
        InputsController controller = new InputsController(activity);
        List<InputsController.Entry> entries = controller.list();
        LinearLayout rows = rows();
        if (entries.isEmpty()) label(rows, activity.getString(R.string.no_inputs));
        for (InputsController.Entry entry : entries) {
            rows.addView(drawer.button(entry.label, DrawerIcon.INPUTS, () -> {
                if (controller.open(entry)) close();
                else { message(R.string.input_unavailable); inputs(); }
            }));
        }
        button(rows, R.string.system_settings, () -> system(false));
        page(R.string.inputs, rows);
    }
    private void system(boolean applications) {
        if (SystemSettings.open(activity, applications)) close();
        else message(R.string.settings_unavailable);
    }
    private void homeButton() {
        int status = HomeKeyService.isEnabled(activity) ? R.string.home_button_enabled
                : R.string.home_button_disabled;
        LinearLayout rows = rows();
        label(rows, activity.getString(status) + "\n\n" + activity.getString(R.string.home_button_instructions));
        button(rows, R.string.open_accessibility,
                () -> shortcut(R.string.accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS));
        page(R.string.home_button_setup, rows);
    }
    private void dailyStandby() {
        StandbyScheduler.reschedule(activity);
        LinearLayout rows = rows();
        label(rows, activity.getString(R.string.standby_description));
        TextView status = new TextView(activity);
        status.setId(R.id.standby_status);
        drawer.text(status, 12);
        status.setPadding(0, drawer.u(8), 0, drawer.u(12));
        status.setText(StandbyScheduler.status(activity));
        rows.addView(status);
        Switch skip = new Switch(activity);
        drawer.style(skip, DrawerIcon.SCREENSAVER);
        skip.setId(R.id.standby_skip_today);
        skip.setText(R.string.standby_skip_today);
        skip.setChecked(StandbyScheduler.skippedToday(activity));
        skip.setOnCheckedChangeListener((view, enabled) -> {
            if (enabled == StandbyScheduler.skippedToday(activity)) return;
            if (!StandbyScheduler.skipToday(activity, enabled)) {
                skip.setChecked(StandbyScheduler.skippedToday(activity));
            }
            status.setText(StandbyScheduler.status(activity));
        });
        rows.addView(skip);
        if (!StandbyScheduler.canSchedule(activity)) {
            button(rows, R.string.standby_open_alarm_access, () -> {
                if (SystemSettings.openExactAlarmAccess(activity)) close();
                else message(R.string.settings_unavailable);
            });
        }
        if (!HomeKeyService.isEnabled(activity)) {
            button(rows, R.string.open_accessibility,
                    () -> shortcut(R.string.accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        page(R.string.daily_standby, rows);
    }
    private void shortcut(int title, String action) {
        if (SystemSettings.openAction(activity, action)) { close(); return; }
        LinearLayout rows = rows();
        label(rows, activity.getString(R.string.shortcut_unavailable, activity.getString(title)));
        button(rows, R.string.system_settings, () -> system(false));
        page(title, rows);
    }
    private void page(int title, View rows) {
        if (!drawer.isSubpage()) {
            View focused = hub.getCurrentFocus();
            if (focused != null) returnFocus = focused;
            mainScroll = drawer.scrollY();
        }
        drawer.page(title, rows, () -> drawer.main(mainContent, returnFocus, mainScroll));
    }
    private void label(LinearLayout rows, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        drawer.text(label, 12);
        label.setPadding(0, drawer.u(8), 0, drawer.u(12));
        rows.addView(label);
    }
    private void showPanel(AlertDialog dialog, Runnable dismissed) {
        Runnable release = beginPanel.get();
        panels.add(dialog);
        dialog.setOnDismissListener(ignored -> {
            panels.remove(dialog);
            release.run();
            dismissed.run();
        });
        try { dialog.show(); attachSounds.accept(dialog); }
        catch (RuntimeException failure) {
            panels.remove(dialog);
            release.run();
            dismissed.run();
            throw failure;
        }
    }
    void close() {
        List<AlertDialog> copy = new ArrayList<>(panels);
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).dismiss();
    }
    private LinearLayout rows() {
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        return rows;
    }
    private Button button(LinearLayout rows, int title, Runnable action) {
        Button button = drawer.button(title, -1, action);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.bottomMargin = drawer.u(8);
        rows.addView(button, layout);
        return button;
    }
    private void message(int message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
}
