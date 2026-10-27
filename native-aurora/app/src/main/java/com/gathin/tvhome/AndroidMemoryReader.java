package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;

final class AndroidMemoryReader implements MemoryCounter.Reader {
    private final Context context;
    AndroidMemoryReader(Context context) { this.context = context.getApplicationContext(); }
    @Override public MemoryReading read() {
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return null;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            Long pss = null;
            try {
                Debug.MemoryInfo[] processes = manager.getProcessMemoryInfo(new int[] {Process.myPid()});
                if (processes != null && processes.length > 0 && processes[0].getTotalPss() > 0) {
                    pss = processes[0].getTotalPss() * 1024L;
                }
            } catch (RuntimeException unavailable) { /* Device RAM can still be displayed. */ }
            return new MemoryReading(pss, info.totalMem - info.availMem, info.totalMem);
        } catch (RuntimeException unavailable) { return null; }
    }
}
