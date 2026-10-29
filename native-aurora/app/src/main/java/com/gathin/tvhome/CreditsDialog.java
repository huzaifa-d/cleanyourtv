package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class CreditsDialog {
    private CreditsDialog() { }
    static AlertDialog create(Activity activity) {
        return new AlertDialog.Builder(activity).setTitle(R.string.about).setMessage(text(activity))
                .setPositiveButton(android.R.string.ok, null).create();
    }
    static String text(Activity activity) {
        StringBuilder text = new StringBuilder(activity.getString(R.string.app_name)).append(' ');
        try {
            text.append(activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName);
        } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) { text.append("1.0"); }
        text.append("\n\nNative Android Views; adapted from CleanYourTV. No cleanup or package disabling.")
                .append("\n\nBrand marks belong to their owners. The default background is bundled with permission; custom MP4s remain local.");
        for (String path : new String[] {"notices/UPSTREAM.txt", "notices/THIRD_PARTY_NOTICES.txt", "notices/LICENSE",
                "aurora/CREDITS.txt", "fonts/OFL.txt", "fonts/NotoSansArabic-OFL.txt",
                "notices/MEDIA3-LICENSE.txt", "notices/SUNCALC-LICENSE.txt"}) {
            try (InputStream input = activity.getAssets().open(path);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                String notice = bytes.toString(StandardCharsets.UTF_8.name());
                if (path.equals("aurora/CREDITS.txt")) {
                    notice = notice.replaceAll("(?s)Weather data:.*?Typeface:", "Typeface:")
                            .replace("All Apps and profile use Flutter Material icons.",
                                    "All Apps and profile use native vector glyphs.");
                }
                text.append("\n\n").append(notice);
            } catch (IOException unavailable) { text.append("\n\nNotice unavailable: ").append(path); }
        }
        return text.toString();
    }
}
