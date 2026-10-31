package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class StandbyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (StandbyScheduler.ACTION_FIRE.equals(intent.getAction())) {
            DailyStandby.Cutoff cutoff = DailyStandby.cutoffForId(
                    intent.getIntExtra(StandbyScheduler.EXTRA_CUTOFF_ID, 2003));
            if (cutoff == null) {
                Log.e("CherryStandby", "Standby alarm has an unknown cutoff");
                return;
            }
            StandbyScheduler.fire(context, intent.getLongExtra(StandbyScheduler.EXTRA_SCHEDULED_AT, 0), cutoff);
        } else {
            StandbyScheduler.reschedule(context);
        }
    }
}
