package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        LinearLayout rows = rows();
        button(rows, R.string.background, () -> background());
        Switch ram = new Switch(activity);
        ram.setId(R.id.ram_switch);
        ram.setText(R.string.show_ram);
        ram.setMinHeight(dp(48));
        ram.setChecked(preferences.showRam());
        ram.setOnCheckedChangeListener((view, enabled) -> {
            preferences.setShowRam(enabled);
            changed.run();
        });
        rows.addView(ram);
        button(rows, R.string.inputs, this::inputs);
        button(rows, R.string.system_settings, () -> system(false));
        button(rows, R.string.app_management, () -> system(true));
        button(rows, R.string.home_button_setup, this::homeButton);
        button(rows, R.string.about, () -> showPanel(CreditsDialog.create(activity), () -> { }));
        hub = new AlertDialog.Builder(activity).setTitle(R.string.profile_settings).setView(scroll(rows))
                .setNegativeButton(android.R.string.cancel, null).create();
        showPanel(hub, () -> { hub = null; dismissed.run(); });
        rows.getChildAt(0).requestFocus();
    }
    private void background() {
        LinearLayout rows = rows();
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.background)
                .setView(scroll(rows)).setNegativeButton(android.R.string.cancel, null).create();
        populateBackground(rows);
        showPanel(dialog, () -> { });
        rows.getChildAt(1).requestFocus();
    }
    private void populateBackground(LinearLayout rows) {
        rows.removeAllViews();
        File directory = files.directory();
        TextView location = new TextView(activity);
        location.setText(activity.getString(R.string.background_directory,
                directory == null ? activity.getString(R.string.storage_unavailable) : directory.getAbsolutePath(),
                preferences.video().isEmpty() ? activity.getString(R.string.default_background) : preferences.video()));
        location.setPadding(dp(8), dp(8), dp(8), dp(8));
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
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            rows.addView(empty);
        }
        for (File file : available) {
            Button item = new Button(activity);
            item.setText(file.getName());
            item.setAllCaps(false);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(R.string.inputs)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.system_settings, (dialog, which) -> system(false));
        if (entries.isEmpty()) builder.setMessage(R.string.no_inputs);
        else builder.setItems(entries.stream().map(entry -> entry.label).toArray(String[]::new), (dialog, index) -> {
            if (controller.open(entries.get(index))) close();
            else {
                message(R.string.input_unavailable);
                inputs();
            }
        });
        showPanel(builder.create(), () -> { });
    }
    private void system(boolean applications) {
        if (SystemSettings.open(activity, applications)) close();
        else message(R.string.settings_unavailable);
    }
    private void homeButton() {
        int status = HomeKeyService.isEnabled(activity) ? R.string.home_button_enabled
                : R.string.home_button_disabled;
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.home_button_setup)
                .setMessage(activity.getString(status) + "\n\n" + activity.getString(R.string.home_button_instructions))
                .setPositiveButton(R.string.open_accessibility, (ignored, which) -> {
                    if (SystemSettings.openAccessibility(activity)) close();
                    else message(R.string.settings_unavailable);
                })
                .setNegativeButton(android.R.string.cancel, null).create();
        showPanel(dialog, () -> { });
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
        rows.setPadding(dp(16), dp(4), dp(16), dp(4));
        return rows;
    }
    private View scroll(LinearLayout rows) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        return scroll;
    }
    private Button button(LinearLayout rows, int title, Runnable action) {
        Button button = new Button(activity);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        rows.addView(button);
        return button;
    }
    private void message(int message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
