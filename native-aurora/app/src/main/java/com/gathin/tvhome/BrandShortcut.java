package com.gathin.tvhome;

enum BrandShortcut {
    NETFLIX("Netflix", "netflix", 25, 45, "com.netflix.ninja", "com.netflix.mediaclient"),
    PRIME("Amazon Prime Video", "prime", 50, 20,
            "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
    YOUTUBE("YouTube", "youtube", 47, 34, "com.google.android.youtube.tv",
            "com.teamsmart.videomanager.tv", "com.teamsmart.videomanager.tv.beta"),
    DISNEY("Disney+", "disney", 60, 34, "com.disney.disneyplus"),
    HOTSTAR("JioHotstar", "hotstar", 44, 40, "in.startv.hotstar", "in.startv.hotstar.dplus"),
    ALL("All Apps", "", 25, 25);

    final String label, assetStem;
    final int width, height;
    final String[] packages;
    BrandShortcut(String label, String assetStem, int width, int height, String... packages) {
        this.label = label;
        this.assetStem = assetStem;
        this.width = width;
        this.height = height;
        this.packages = packages;
    }
}
