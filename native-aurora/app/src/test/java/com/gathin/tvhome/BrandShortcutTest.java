package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class BrandShortcutTest {
    @Test public void prefersCustomPrimeBeforeStockFallbacks() {
        assertArrayEquals(new String[] {
                "com.amazon.amazonvideo.livingroom.mod",
                "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"
        }, BrandShortcut.PRIME.packages);
    }

    @Test public void prefersSmartTubeBeforeExistingYouTubeFallbacks() {
        assertArrayEquals(new String[] {
                "org.smarttube.stable", "com.google.android.youtube.tv",
                "com.teamsmart.videomanager.tv", "com.teamsmart.videomanager.tv.beta"
        }, BrandShortcut.YOUTUBE.packages);
    }

    @Test public void prefersCustomDisneyBeforeStockFallback() {
        assertArrayEquals(new String[] {
                "com.disney.disneyplus.mod", "com.disney.disneyplus"
        }, BrandShortcut.DISNEY.packages);
    }

    @Test public void prefersCustomHotstarBeforeStockFallbacks() {
        assertArrayEquals(new String[] {
                "in.startv.hotstar.mod", "in.startv.hotstar", "in.startv.hotstar.dplus"
        }, BrandShortcut.HOTSTAR.packages);
    }

    @Test public void includesSonyAppleTvBeforeExistingFallbacks() {
        assertArrayEquals(new String[] {
                "com.apple.atve.sony.appletv",
                "com.apple.atve.androidtv", "com.apple.atve.androidtv.appletv"
        }, BrandShortcut.APPLE_TV.packages);
    }
}
