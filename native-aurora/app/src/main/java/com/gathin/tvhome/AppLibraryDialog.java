package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Popup ownership and recycled icon guards adapted from CleanYourTV (MIT).
final class AppLibraryDialog {
    private final Activity activity;
    private final AppCatalog catalog;
    private final LibraryLoads loads = new LibraryLoads();
    private AlertDialog dialog;
    private RecyclerView grid;
    private TextView empty;
    private boolean initialFocus;
    private String pendingFocus;
    private final Consumer<List<AppCatalog.Entry>> updated;
    private final Consumer<AlertDialog> attachSounds;
    private final List<AppCatalog.Entry> entries = new ArrayList<>();

    AppLibraryDialog(Activity activity, AppCatalog catalog, Consumer<List<AppCatalog.Entry>> updated,
            Consumer<AlertDialog> attachSounds) {
        this.activity = activity;
        this.catalog = catalog;
        this.updated = updated;
        this.attachSounds = attachSounds;
    }
    void show(View anchor, Runnable dismissed) {
        if (isShowing()) return;
        grid = new RecyclerView(activity);
        initialFocus = true;
        pendingFocus = null;
        grid.setLayoutManager(new GridLayoutManager(activity, 4));
        grid.setPadding(dp(16), dp(12), dp(16), dp(12));
        grid.setId(R.id.app_grid);
        grid.setAdapter(new AppsAdapter());
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        empty = new TextView(activity);
        empty.setText(R.string.no_apps);
        empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        empty.setVisibility(View.GONE);
        content.addView(empty);
        int height = Math.min(dp(350), activity.getResources().getDisplayMetrics().heightPixels - dp(160));
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(dp(120), height)));
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.all_apps)
                .setView(content).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnDismissListener(ignored -> {
            loads.start(false);
            grid.setAdapter(null);
            entries.clear();
            dialog = null;
            dismissed.run();
        });
        try { dialog.show(); attachSounds.accept(dialog); }
        catch (RuntimeException failure) { dialog = null; dismissed.run(); throw failure; }
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                Math.min(dp(780), activity.getResources().getDisplayMetrics().widthPixels - dp(48)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        refresh();
    }
    void refresh() {
        if (!isShowing()) return;
        int generation = loads.start(initialFocus);
        catalog.load(result -> {
            if (!isShowing() || !loads.isLatest(generation)) return;
            View focused = grid.findFocus();
            RecyclerView.ViewHolder holder = focused == null ? null : grid.findContainingViewHolder(focused);
            int previous = holder == null ? RecyclerView.NO_POSITION : holder.getBindingAdapterPosition();
            String selected = previous >= 0 && previous < entries.size()
                    ? entries.get(previous).packageName : null;
            entries.clear();
            entries.addAll(result);
            updated.accept(result);
            empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            grid.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
            boolean reveal = loads.takeReveal();
            initialFocus = false;
            int target = 0;
            if (selected != null) {
                target = Math.min(previous, Math.max(0, entries.size() - 1));
                for (int i = 0; i < entries.size(); i++) {
                    if (selected.equals(entries.get(i).packageName)) { target = i; break; }
                }
            }
            pendingFocus = entries.isEmpty() || (!reveal && selected == null)
                    ? null : entries.get(target).packageName;
            grid.getAdapter().notifyDataSetChanged();
            if (pendingFocus != null) grid.scrollToPosition(target);
            if (entries.isEmpty() && (reveal || selected != null)) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
            }
        });
    }
    boolean isShowing() { return dialog != null && dialog.isShowing(); }
    void close() { if (dialog != null) dialog.dismiss(); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        String bound;
        Holder(LinearLayout card, ImageView icon, TextView label) {
            super(card);
            this.icon = icon;
            this.label = label;
        }
    }
    private final class AppsAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(6), dp(6), dp(6));
            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            card.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(115)));
            card.setOnFocusChangeListener((view, focus) -> {
                GradientDrawable background = new GradientDrawable();
                background.setColor(focus ? 0x330779FF : 0x08FFFFFF);
                background.setCornerRadius(dp(4));
                background.setStroke(dp(1), focus ? Color.WHITE : Color.TRANSPARENT);
                view.setBackground(background);
            });
            ImageView icon = new ImageView(activity);
            card.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));
            TextView label = new TextView(activity);
            label.setTextColor(Color.WHITE);
            label.setTextSize(13);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            card.addView(label, new LinearLayout.LayoutParams(-1, dp(38)));
            return new Holder(card, icon, label);
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            AppCatalog.Entry entry = entries.get(position);
            holder.bound = entry.packageName;
            holder.label.setText(entry.label);
            holder.itemView.setContentDescription(entry.label);
            holder.icon.setImageResource(R.drawable.aurora_apps);
            catalog.icon(entry, dp(58), bitmap -> {
                if (isShowing() && entry.packageName.equals(holder.bound) && bitmap != null) {
                    holder.icon.setImageBitmap(bitmap);
                }
            });
            holder.itemView.setOnClickListener(v -> {
                if (catalog.launch(entry)) close();
                else Toast.makeText(activity, activity.getString(R.string.cannot_open, entry.label),
                        Toast.LENGTH_SHORT).show();
            });
            if (entry.packageName.equals(pendingFocus)) holder.itemView.post(() -> {
                if (isShowing() && entry.packageName.equals(holder.bound)
                        && entry.packageName.equals(pendingFocus) && holder.itemView.requestFocus()) {
                    pendingFocus = null;
                }
            });
        }
        @Override public int getItemCount() { return entries.size(); }
        @Override public void onViewRecycled(@NonNull Holder holder) {
            holder.bound = null;
            holder.icon.setImageDrawable(null);
        }
    }
}
