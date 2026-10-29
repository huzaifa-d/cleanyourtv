package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CherryControlsTest {
    @Test public void visibleBrandAndHomeServiceAreCherryWithoutChangingInstalledIdentity() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.huzaifa.aurora.nativehome", context.getPackageName());
        assertEquals("Cherry Launcher", context.getApplicationInfo()
                .loadLabel(context.getPackageManager()).toString());
        ServiceInfo service = context.getPackageManager().getServiceInfo(
                new ComponentName(context, HomeKeyService.class), PackageManager.GET_META_DATA);
        assertEquals("Cherry Launcher Home button", service.loadLabel(context.getPackageManager()).toString());
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, service.permission);
        assertTrue(service.exported);
        try (XmlResourceParser config = service.loadXmlMetaData(
                context.getPackageManager(), "android.accessibilityservice")) {
            assertNotNull(config);
            while (config.next() != XmlResourceParser.START_TAG) { }
            String android = "http://schemas.android.com/apk/res/android";
            assertTrue(config.getAttributeBooleanValue(android, "canRequestFilterKeyEvents", false));
            assertFalse(config.getAttributeBooleanValue(android, "canRetrieveWindowContent", true));
        }
    }

    @Test public void gearIsFocusableBesideClockAndReturnsToRememberedApp() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            scene.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.disney).requestFocus());
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
                assertTrue(activity.findViewById(R.id.profile).hasFocus());
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                assertTrue(activity.findViewById(R.id.network_status).hasFocus());
                assertTrue(activity.findViewById(R.id.network_status).performClick());
                assertTrue(activity.findViewById(R.id.network_status).hasFocus());
                assertTrue(activity.hasWindowFocus());
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                assertTrue(activity.findViewById(R.id.quick_settings).hasFocus());
                Rect gear = new Rect(), clock = new Rect(), network = new Rect();
                assertTrue(activity.findViewById(R.id.quick_settings).getGlobalVisibleRect(gear));
                assertTrue(activity.findViewById(R.id.clock).getGlobalVisibleRect(clock));
                assertTrue(activity.findViewById(R.id.network_status).getGlobalVisibleRect(network));
                assertTrue(network.left > clock.right);
                assertTrue(gear.left > network.right);
                assertEquals(63, activity.findViewById(R.id.profile).getWidth());
                assertEquals(63, activity.findViewById(R.id.profile).getHeight());
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                assertTrue(activity.findViewById(R.id.disney).hasFocus());
            });
        }
    }
}
