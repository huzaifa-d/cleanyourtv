package com.gathin.tvhome;

import android.content.Context;
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
import java.util.Locale;
import java.util.function.IntConsumer;

final class AuroraHomeView extends FrameLayout {
    static final int[] TILE_IDS = {
        R.id.netflix, R.id.prime, R.id.youtube, R.id.disney, R.id.hotstar, R.id.apple_tv, R.id.all_apps
    };
    private final FrameLayout composition;
    private final BlossomTile[] tiles = new BlossomTile[BrandShortcut.values().length];
    private final PetalView petals;
    private final OutputResolutionIconView profile, settings;
    private final TextView greeting, clock, memory;
    private final Typeface typeface, urduTypeface;
    private final HomeNavigation navigation;
    private final ImageView still;
    private final OutputResolutionIconView network;
    private float iconScale;
    private boolean greetingActive, greetingUrdu;
    private int greetingGeneration;
    private final Runnable greetingTick = new Runnable() {
        @Override public void run() {
            if (!greetingActive) return;
            int token = greetingGeneration;
            boolean nextUrdu = !greetingUrdu;
            greeting.animate().alpha(0f).translationY(-4f).setDuration(200).withEndAction(() -> {
                if (!greetingActive || token != greetingGeneration) return;
                greetingUrdu = nextUrdu;
                updateGreeting();
                greeting.setTranslationY(4f);
                greeting.animate().alpha(1f).translationY(0f).setDuration(200).start();
            }).start();
            postDelayed(this, nextUrdu ? 10000 : 15000);
        }
    };

    AuroraHomeView(Context context, HomeNavigation navigation, IntConsumer selected) {
        super(context);
        this.navigation = navigation;
        setBackgroundColor(0xFF171B35);
        typeface = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto.ttf");
        urduTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/NotoSansArabic.ttf");
        composition = new FrameLayout(context);
        composition.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        composition.setClipChildren(false);
        composition.setId(R.id.home_root);
        composition.setFocusable(true);
        composition.setFocusableInTouchMode(true);
        addView(composition);

        still = new ImageView(context);
        still.setId(R.id.background_still);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        still.setImageBitmap(decode("aurora/cherry-blossom.jpg", 2000));
        composition.addView(still, position(960, 540, 0, 0));
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x33000000, Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.TRANSPARENT, 0x11000000}));
        composition.addView(shade, position(960, 540, 0, 0));
        petals = new PetalView(context);
        petals.setId(R.id.petals);
        composition.addView(petals, position(960, 540, 0, 0));

        profile = new OutputResolutionIconView(context);
        profile.setId(R.id.profile);
        profile.setImageResource(R.drawable.aurora_profile);
        profile.setPadding(6, 6, 6, 6);
        profile.setContentDescription(context.getString(R.string.profile_settings));
        profile.setFocusable(true);
        profile.setFocusableInTouchMode(true);
        profile.setOnClickListener(v -> selected.accept(HomeNavigation.PROFILE));
        profile.setOnFocusChangeListener((v, focused) -> {
            profile.setBackground(frame(focused, true));
            if (focused) navigation.select(HomeNavigation.PROFILE);
        });
        profile.setBackground(frame(false, true));
        composition.addView(profile, position(63, 63, 33, 18));
        greeting = text(15.6f);
        greeting.setId(R.id.greeting);
        greeting.setSingleLine(true);
        greeting.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        composition.addView(greeting, position(450, 64, 110, 18));
        clock = text(17.3f);
        clock.setId(R.id.clock);
        clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clock.setFontFeatureSettings("tnum");
        composition.addView(clock, position(160, 32, 662, 34));
        network = new OutputResolutionIconView(context);
        network.setId(R.id.network_status);
        network.setScaleType(ImageView.ScaleType.FIT_CENTER);
        network.setPadding(6, 6, 6, 6);
        network.setFocusable(true);
        network.setFocusableInTouchMode(true);
        network.setOnClickListener(v -> selected.accept(HomeNavigation.NETWORK));
        network.setOnFocusChangeListener((v, focused) -> {
            network.setBackground(frame(focused, true));
            if (focused) navigation.select(HomeNavigation.NETWORK);
        });
        network.setBackground(frame(false, true));
        composition.addView(network, position(32, 32, 834, 34));
        settings = new OutputResolutionIconView(context);
        settings.setId(R.id.quick_settings);
        settings.setImageResource(R.drawable.cherry_settings);
        settings.setPadding(5, 5, 5, 5);
        settings.setContentDescription(context.getString(R.string.quick_settings));
        settings.setFocusable(true);
        settings.setFocusableInTouchMode(true);
        settings.setOnClickListener(v -> selected.accept(HomeNavigation.QUICK_SETTINGS));
        settings.setOnFocusChangeListener((v, focused) -> {
            settings.setBackground(frame(focused, true));
            if (focused) navigation.select(HomeNavigation.QUICK_SETTINGS);
        });
        settings.setBackground(frame(false, true));
        composition.addView(settings, position(32, 32, 882, 34));
        setNetworkStatus(NetworkMonitor.Status.OFFLINE);

        Bitmap blossom = decode("aurora/cherry/blossom.png", 673);
        int[][] centres = {{134, 178}, {346, 146}, {557, 189}, {768, 157},
                {470, 319}, {230, 335}, {720, 346}};
        int[] angles = {-28, 46, -84, 118, -142, 76, 164};
        for (BrandShortcut brand : BrandShortcut.values()) {
            int index = brand.ordinal();
            BlossomTile tile = new BlossomTile(context, brand, blossom, angles[index],
                    () -> navigation.select(index));
            tile.setId(TILE_IDS[index]);
            tile.setOnClickListener(v -> selected.accept(index));
            tiles[index] = tile;
            composition.addView(tile, position(BlossomTile.SIZE, BlossomTile.SIZE,
                    centres[index][0] - BlossomTile.SIZE / 2, centres[index][1] - BlossomTile.SIZE / 2));
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
        drawable.setShape(circle ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!circle) drawable.setCornerRadius(4);
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

    private void updateIconResolution(float scale) {
        if (scale <= 0 || iconScale == scale) return;
        iconScale = scale;
        profile.setOutputScale(scale);
        settings.setOutputScale(scale);
        network.setOutputScale(scale);
        for (BrandShortcut brand : BrandShortcut.values()) {
            int index = brand.ordinal();
            BlossomTile tile = tiles[index];
            ImageView mark = tile.mark;
            if (brand == BrandShortcut.ALL) {
                ((OutputResolutionIconView) mark).setOutputScale(scale * BlossomTile.MAX_FOCUS_SCALE);
            } else {
                int width = (int) Math.ceil(mark.getLayoutParams().width * BlossomTile.MAX_FOCUS_SCALE * scale);
                tile.setLogos(decode("aurora/icons/" + brand.assetStem + "-white.png", width),
                        decode("aurora/icons/" + brand.assetStem + "-colour.png", width));
            }
        }
    }

    void applyFocus(int target) {
        if (target == HomeNavigation.IDLE) composition.requestFocus();
        else if (target == HomeNavigation.PROFILE) profile.requestFocus();
        else if (target == HomeNavigation.NETWORK) network.requestFocus();
        else if (target == HomeNavigation.QUICK_SETTINGS) settings.requestFocus();
        else tiles[target].requestFocus();
    }
    void setProfilePhoto(Bitmap photo) { profile.setBitmapContent(photo); }
    void setMemoryText(String reading) { memory.setText(reading); }
    void setHomeActive(boolean active) {
        setGreetingActive(active);
        petals.setActive(active);
        for (BlossomTile tile : tiles) tile.setActive(active);
    }
    void setGreetingActive(boolean active) {
        if (greetingActive == active) return;
        greetingActive = active;
        greetingGeneration++;
        removeCallbacks(greetingTick);
        greeting.animate().cancel();
        greeting.setAlpha(1f);
        greeting.setTranslationY(0f);
        greetingUrdu = false;
        updateGreeting();
        if (active) postDelayed(greetingTick, 15000);
    }
    void setNetworkStatus(NetworkMonitor.Status status) {
        network.setImageResource(status.drawable);
        network.setContentDescription(getContext().getString(status.description));
    }
    void setMemoryVisible(boolean visible) { memory.setVisibility(visible ? VISIBLE : GONE); }
    void setVideoVisible(boolean visible) {
        still.setVisibility(visible ? INVISIBLE : VISIBLE);
        setBackgroundColor(visible ? Color.TRANSPARENT : 0xFF171B35);
    }
    void updateClock() {
        Calendar now = Calendar.getInstance();
        updateGreeting();
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a", now));
    }
    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int label = greetingUrdu ? hour < 12 ? R.string.good_morning_urdu
                : hour < 18 ? R.string.good_afternoon_urdu : R.string.good_evening_urdu
                : hour < 12 ? R.string.good_morning
                : hour < 18 ? R.string.good_afternoon : R.string.good_evening;
        greeting.setTypeface(greetingUrdu ? urduTypeface : typeface);
        greeting.setIncludeFontPadding(greetingUrdu);
        greeting.setTextLocale(greetingUrdu ? new Locale("ur") : Locale.ENGLISH);
        greeting.setTextDirection(greetingUrdu ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        greeting.setText(getContext().getString(label));
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
        updateIconResolution(scale);
        composition.layout(x, y, x + 960, y + 540);
        composition.setPivotX(0);
        composition.setPivotY(0);
        composition.setScaleX(scale);
        composition.setScaleY(scale);
    }
}
