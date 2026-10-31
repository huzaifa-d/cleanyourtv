package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.List;

final class CherryDrawer extends LinearLayout {
    static final int TEXT = 0xfffff4f4, FOCUSED_TEXT = 0xff3b1118;
    private final float scale, fontScale;
    private final LinearLayout header, mainHeader;
    private final ScrollView scroll;
    private final ImageButton settings, profile;
    private Runnable back;

    CherryDrawer(Activity activity, Runnable openSettings, Runnable openProfile) {
        super(activity);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = Math.min(metrics.widthPixels / 960f, metrics.heightPixels / 540f);
        fontScale = getResources().getConfiguration().fontScale;
        setTag("cherry_drawer");
        setOrientation(VERTICAL);
        setPadding(u(18), u(20), u(18), u(10));
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xff65192e, 0xff280c17});
        panel.setCornerRadius(u(25));
        panel.setStroke(Math.max(1, u(1)), 0x60bd5973);
        setBackground(panel);
        header = new LinearLayout(activity);
        boolean largeText = fontScale > 1.3f;
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT,
                u(largeText ? 68 * fontScale : 80)));
        mainHeader = new LinearLayout(activity);
        mainHeader.setOrientation(largeText ? VERTICAL : HORIZONTAL);
        LinearLayout clocks = new LinearLayout(activity);
        clocks.setOrientation(VERTICAL);
        TextClock date = new TextClock(activity);
        date.setFormat12Hour("EEE, d MMM");
        date.setFormat24Hour("EEE, d MMM");
        text(date, 12);
        date.setAlpha(.8f);
        clocks.addView(date);
        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        text(time, 26);
        clocks.addView(time);
        mainHeader.addView(clocks, largeText
                ? new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                : new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        LinearLayout actions = new LinearLayout(activity);
        mainHeader.addView(actions);
        settings = icon(DrawerIcon.SETTINGS, R.string.system_settings, openSettings);
        profile = icon(DrawerIcon.PROFILE, R.string.switch_profile, openProfile);
        actions.addView(settings, new LayoutParams(u(34), u(34)));
        LayoutParams avatar = new LayoutParams(u(34), u(34));
        avatar.leftMargin = u(10);
        actions.addView(profile, avatar);
        scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
    }

    int u(float value) { return Math.max(1, Math.round(value * scale)); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(u(radius));
        return result;
    }

    private StateListDrawable background(int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, shape(0xfff5dce7, radius));
        states.addState(new int[] {android.R.attr.state_pressed}, shape(0xfff5dce7, radius));
        states.addState(new int[0], shape(0x458d3046, radius));
        return states;
    }

    void text(TextView view, int size) {
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale * fontScale);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    void style(TextView view, int glyph) {
        view.setId(View.generateViewId());
        view.setMinimumHeight(u(56));
        view.setMinHeight(u(56));
        view.setPadding(u(8), u(4), u(8), u(4));
        text(view, 12);
        view.setTextColor(new ColorStateList(new int[][] {
                {android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[] {FOCUSED_TEXT, FOCUSED_TEXT, TEXT}));
        view.setBackground(background(15));
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        if (glyph >= 0) {
            view.setCompoundDrawablesRelative(new DrawerIcon(getContext(), glyph, u(24)), null, null, null);
            view.setCompoundDrawablePadding(u(8));
        }
    }

    Button button(int title, int glyph, Runnable action) {
        return button(getContext().getString(title), glyph, action);
    }

    Button button(String title, int glyph, Runnable action) {
        Button button = new Button(getContext());
        style(button, glyph);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setText(title);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private ImageButton icon(int glyph, int label, Runnable action) {
        ImageButton button = new ImageButton(getContext());
        button.setId(View.generateViewId());
        button.setContentDescription(getContext().getString(label));
        button.setImageDrawable(new DrawerIcon(getContext(), glyph, u(24)));
        button.setBackground(background(40));
        button.setPadding(u(5), u(5), u(5), u(5));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    View grid(List<View> tiles) {
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(VERTICAL);
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setBaselineAligned(false);
            LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (i > 0) rowParams.topMargin = u(8);
            grid.addView(row, rowParams);
            LayoutParams first = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            first.rightMargin = u(8);
            row.addView(tiles.get(i), first);
            row.addView(i + 1 < tiles.size() ? tiles.get(i + 1) : new View(getContext()),
                    new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        }
        settings.setNextFocusLeftId(settings.getId());
        settings.setNextFocusRightId(profile.getId());
        settings.setNextFocusUpId(settings.getId());
        settings.setNextFocusDownId(tiles.get(0).getId());
        profile.setNextFocusLeftId(settings.getId());
        profile.setNextFocusRightId(profile.getId());
        profile.setNextFocusUpId(profile.getId());
        profile.setNextFocusDownId(tiles.get(1).getId());
        for (int i = 0; i < tiles.size(); i++) {
            View tile = tiles.get(i);
            tile.setNextFocusLeftId(tiles.get(i % 2 == 0 ? i : i - 1).getId());
            tile.setNextFocusRightId(tiles.get(i % 2 == 0 && i + 1 < tiles.size() ? i + 1 : i).getId());
            tile.setNextFocusUpId(i < 2 ? (i == 0 ? settings : profile).getId() : tiles.get(i - 2).getId());
            tile.setNextFocusDownId(tiles.get(i + 2 < tiles.size()
                    ? i + 2 : i + 1 < tiles.size() ? i + 1 : i).getId());
        }
        return grid;
    }

    private void content(View view) {
        scroll.removeAllViews();
        scroll.addView(view);
        scroll.scrollTo(0, 0);
    }

    void main(View view, View focus, int scrollY) {
        back = null;
        header.setGravity(Gravity.TOP);
        header.removeAllViews();
        header.addView(mainHeader, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        content(view);
        focus.requestFocusFromTouch();
        scroll.post(() -> {
            if (back == null && view.getParent() == scroll) {
                scroll.scrollTo(0, scrollY);
                focus.requestFocusFromTouch();
            }
        });
    }

    void page(int title, View view, Runnable returnMain) {
        back = returnMain;
        header.removeAllViews();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton arrow = icon(DrawerIcon.BACK, R.string.back_to_menu, returnMain);
        header.addView(arrow, new LayoutParams(u(34), u(34)));
        TextView heading = new TextView(getContext());
        heading.setText(title);
        text(heading, 20);
        LayoutParams label = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        label.leftMargin = u(12);
        header.addView(heading, label);
        content(view);
        arrow.requestFocus();
    }

    boolean isSubpage() { return back != null; }
    int scrollY() { return scroll.getScrollY(); }
    boolean back() {
        if (back == null) return false;
        back.run();
        return true;
    }
}
