package com.gathin.tvhome;

import java.util.Locale;

final class MemoryReading {
    final Long appPssBytes;
    final long usedBytes, totalBytes;
    MemoryReading(Long appPssBytes, long usedBytes, long totalBytes) {
        if (totalBytes <= 0 || usedBytes < 0 || usedBytes > totalBytes
                || (appPssBytes != null && appPssBytes <= 0)) {
            throw new IllegalArgumentException("Invalid memory reading");
        }
        this.appPssBytes = appPssBytes;
        this.usedBytes = usedBytes;
        this.totalBytes = totalBytes;
    }
    String display() {
        String app = appPssBytes == null ? "unavailable"
                : String.format(Locale.getDefault(), "%.0f MiB", appPssBytes / 1048576d);
        return String.format(Locale.getDefault(),
                "App RAM (PSS) %s / %.2f GiB%nTV RAM %.2f GiB / %.2f GiB",
                app, totalBytes / 1073741824d, usedBytes / 1073741824d, totalBytes / 1073741824d);
    }
}
