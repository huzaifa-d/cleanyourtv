package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Intent;
import android.media.tv.TvContract;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InputsControllerTest {
    @Test public void passthroughKeepsExactHdmiCecChildId() {
        String id = "com.sony.dtv.tvinput/.HdmiService/HDMI100004";
        Intent intent = InputsController.passthroughIntent(id);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(TvContract.buildChannelUriForPassthroughInput(id), intent.getData());
        assertEquals(TvContract.Channels.CONTENT_ITEM_TYPE, intent.getType());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
    }
    @Test public void tunerUsesChannelCollectionNotPassthrough() {
        String id = "com.sony.dtv.tvinput/.TunerService/HW0";
        Intent intent = InputsController.tunerIntent(id);
        assertEquals(TvContract.buildChannelsUriForInput(id), intent.getData());
        assertEquals(TvContract.Channels.CONTENT_TYPE, intent.getType());
        assertFalse(TvContract.isChannelUriForPassthroughInput(intent.getData()));
    }
    @Test public void unavailableHandlersFailWithoutLeavingHome() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                assertNull(SystemSettings.intent(activity, "com.huzaifa.aurora.NO_HANDLER"));
                assertFalse(new InputsController(activity).open(new InputsController.Entry(
                        "Unavailable", new Intent("com.huzaifa.aurora.NO_HANDLER"))));
            });
        }
    }
}
