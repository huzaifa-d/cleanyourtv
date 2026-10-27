package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.function.IntConsumer;

final class AuroraHomeView extends FrameLayout {
    static final int[] TILE_IDS = {
        R.id.netflix, R.id.prime, R.id.youtube, R.id.disney, R.id.hotstar, R.id.all_apps
    };
    private final FrameLayout composition;
    private final View[] tiles = new View[6];
    private final ImageView[] marks = new ImageView[6];
    private final Bitmap[][] logos = new Bitmap[5][2];
    private final ImageView profile;
    private final TextView greeting, clock, memory;
    private final Typeface typeface;
    private final HomeNavigation navigation;
    private final ImageView still;

    AuroraHomeView(Context context, HomeNavigation navigation, IntConsumer selected) {
        super(context);
        this.navigation = navigation;
        setBackgroundColor(0xFF171B35);
        typeface = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto.ttf");
        composition = new FrameLayout(context);
        composition.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        composition.setId(R.id.home_root);
        composition.setFocusable(true);
        composition.setFocusableInTouchMode(true);
        addView(composition);

        still = new ImageView(context);
        still.setId(R.id.background_still);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        still.setImageBitmap(decode("aurora/aurora-i-4k.jpg", 1280));
        composition.addView(still, position(960, 540, 0, 0));
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x20000000, Color.TRANSPARENT, 0x4D000000}));
        composition.addView(shade, position(960, 540, 0, 0));

        profile = new ImageView(context);
        profile.setId(R.id.profile);
        profile.setImageResource(R.drawable.aurora_profile);
        profile.setPadding(2, 2, 2, 2);
        profile.setContentDescription(context.getString(R.string.profile_settings));
        profile.setFocusable(true);
        profile.setFocusableInTouchMode(true);
        profile.setOnClickListener(v -> selected.accept(HomeNavigation.PROFILE));
        profile.setOnFocusChangeListener((v, focused) -> {
            profile.setBackground(frame(focused, true));
            if (focused) navigation.select(HomeNavigation.PROFILE);
        });
        profile.setBackground(frame(false, true));
        composition.addView(profile, position(21, 21, 54, 39));
        greeting = text(15.6f);
        composition.addView(greeting, position(500, 25, 85, 38));
        clock = text(17.3f);
        clock.setId(R.id.clock);
        clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clock.setFontFeatureSettings("tnum");
        composition.addView(clock, position(180, 25, 726, 37));

        for (BrandShortcut brand : BrandShortcut.values()) {
            int index = brand.ordinal();
            FrameLayout tile = new FrameLayout(context);
            tile.setId(TILE_IDS[index]);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(true);
            tile.setContentDescription(brand.label);
            tile.setOnClickListener(v -> selected.accept(index));
            ImageView mark = new ImageView(context);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (index < 5) {
                logos[index][0] = decode("aurora/icons/" + brand.assetStem + "-white.png", 256);
                logos[index][1] = decode("aurora/icons/" + brand.assetStem + "-colour.png", 256);
                mark.setImageBitmap(logos[index][0]);
            } else mark.setImageResource(R.drawable.aurora_apps);
            tile.addView(mark, new FrameLayout.LayoutParams(brand.width, brand.height, Gravity.CENTER));
            tiles[index] = tile;
            marks[index] = mark;
            tile.setOnFocusChangeListener((v, focused) -> {
                tile.setBackground(frame(focused, false));
                if (index < 5) mark.setImageBitmap(logos[index][focused ? 1 : 0]);
                else mark.setImageTintList(ColorStateList.valueOf(focused ? 0xFF0779FF : Color.WHITE));
                if (focused) navigation.select(index);
            });
            composition.addView(tile, position(65, 88, Math.round(181 + index * 106.6f), 412));
        }
        memory = text(9f);
        memory.setId(R.id.memory);
        memory.setTextColor(0xBFFFFFFF);
        memory.setGravity(Gravity.END | Gravity.BOTTOM);
        memory.setFontFeatureSettings("tnum");
        composition.addView(memory, position(300, 36, 636, 492));
        updateClock();
        composition.requestFocus();
    }

    private TextView text(float size) {
        TextView view = new TextView(getContext());
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        view.setTypeface(typeface, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private static LayoutParams position(int width, int height, int left, int top) {
        LayoutParams params = new LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        return params;
    }

    private GradientDrawable frame(boolean focused, boolean circle) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(circle ? 0x16FFFFFF : focused ? 0x08FFFFFF : Color.TRANSPARENT);
        drawable.setCornerRadius(circle ? 21 : 4);
        drawable.setStroke(1, focused ? 0xA0FFFFFF : circle ? 0x55FFFFFF : Color.TRANSPARENT);
        return drawable;
    }

    private Bitmap decode(String path, int width) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = getContext().getAssets().open(path)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            int sample = 1;
            while (options.outWidth / (sample * 2) >= width) sample *= 2;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            try (InputStream input = getContext().getAssets().open(path)) {
                Bitmap decoded = BitmapFactory.decodeStream(input, null, options);
                if (decoded == null || decoded.getWidth() <= width) return decoded;
                Bitmap scaled = Bitmap.createScaledBitmap(decoded, width,
                        Math.max(1, Math.round(decoded.getHeight() * width / (float) decoded.getWidth())), true);
                decoded.recycle();
                return scaled;
            }
        } catch (IOException unavailable) { return null; }
    }

    void applyFocus(int target) {
        if (target == HomeNavigation.IDLE) composition.requestFocus();
        else if (target == HomeNavigation.PROFILE) profile.requestFocus();
        else tiles[target].requestFocus();
    }
    void setMemoryText(String reading) { memory.setText(reading); }
    void setMemoryVisible(boolean visible) { memory.setVisibility(visible ? VISIBLE : GONE); }
    void setVideoVisible(boolean visible) {
        still.setVisibility(visible ? INVISIBLE : VISIBLE);
        setBackgroundColor(visible ? Color.TRANSPARENT : 0xFF171B35);
    }
    void updateClock() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(getContext().getString(hour < 12 ? R.string.good_morning
                : hour < 18 ? R.string.good_afternoon : R.string.good_evening));
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a", now));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        composition.measure(MeasureSpec.makeMeasureSpec(960, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(540, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        float scale = Math.min(getWidth() / 960f, getHeight() / 540f);
        int x = Math.round((getWidth() - 960 * scale) / 2);
        int y = Math.round((getHeight() - 540 * scale) / 2);
        composition.layout(x, y, x + 960, y + 540);
        composition.setPivotX(0);
        composition.setPivotY(0);
        composition.setScaleX(scale);
        composition.setScaleY(scale);
    }
}
