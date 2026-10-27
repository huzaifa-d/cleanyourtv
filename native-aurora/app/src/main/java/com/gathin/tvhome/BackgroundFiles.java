package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class BackgroundFiles {
    private final File root;
    BackgroundFiles(File root) { this.root = root; }
    File directory() { return root; }
    static boolean validName(String filename) {
        return filename != null && !filename.isEmpty()
                && !filename.contains("/") && !filename.contains("\\")
                && filename.toLowerCase(Locale.ROOT).endsWith(".mp4");
    }
    File resolve(String filename) {
        if (root == null || !validName(filename)) return null;
        try {
            File parent = root.getCanonicalFile();
            File candidate = new File(parent, filename).getCanonicalFile();
            return parent.equals(candidate.getParentFile()) && candidate.isFile()
                    ? candidate : null;
        } catch (IOException | SecurityException failure) {
            return null;
        }
    }
    List<File> list() {
        List<File> result = new ArrayList<>();
        File[] children;
        try {
            children = root == null ? null : root.listFiles();
        } catch (SecurityException unavailable) {
            children = null;
        }
        if (children != null) {
            for (File child : children) {
                File resolved = resolve(child.getName());
                if (resolved != null) result.add(resolved);
            }
        }
        result.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(File::getName));
        return result;
    }
}
