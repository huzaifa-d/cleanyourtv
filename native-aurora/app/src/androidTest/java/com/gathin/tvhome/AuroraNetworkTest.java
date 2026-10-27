package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraNetworkTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test public void emptyCapabilitiesAreOfflineAndRealCapabilitiesMatchSystem() {
        assertEquals(NetworkMonitor.Status.OFFLINE, NetworkMonitor.status(null));
        assertEquals(NetworkMonitor.Status.OFFLINE, NetworkMonitor.status(new NetworkCapabilities()));
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        org.junit.Assume.assumeNotNull(caps);
        NetworkMonitor.Status expected = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ? NetworkMonitor.Status.OFFLINE
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? NetworkMonitor.Status.ETHERNET
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? NetworkMonitor.Status.WIFI
                : NetworkMonitor.Status.OTHER;
        assertEquals(expected, NetworkMonitor.status(caps));
    }

    @Test @SdkSuppress(minSdkVersion = 28)
    public void ignoresOldNetworkLossAndCallbacksAfterStopOrRestart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<NetworkMonitor.Status> readings = new ArrayList<>();
            NetworkMonitor monitor = new NetworkMonitor(context,
                    new Handler(Looper.getMainLooper()), readings::add);
            try {
                monitor.start();
                ConnectivityManager.NetworkCallback first =
                        (ConnectivityManager.NetworkCallback) field(monitor, "callback");
                monitor.start();
                assertSame(first, field(monitor, "callback"));
                ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
                Network current = manager.getActiveNetwork();
                NetworkCapabilities caps = manager.getNetworkCapabilities(current);
                org.junit.Assume.assumeNotNull(current, caps);
                NetworkMonitor.Status expected = NetworkMonitor.status(caps);
                org.junit.Assume.assumeTrue(expected != NetworkMonitor.Status.OFFLINE);
                Network old = Network.fromNetworkHandle(current.getNetworkHandle() + (1L << 32));
                first.onAvailable(current);
                first.onCapabilitiesChanged(current, caps);
                assertEquals(expected, readings.get(readings.size() - 1));
                first.onAvailable(old);
                first.onAvailable(current);
                first.onCapabilitiesChanged(current, caps);
                first.onLost(old);
                first.onCapabilitiesChanged(old, new NetworkCapabilities());
                assertEquals(expected, readings.get(readings.size() - 1));
                first.onLost(current);
                assertEquals(NetworkMonitor.Status.OFFLINE, readings.get(readings.size() - 1));
                monitor.stop();
                int count = readings.size();
                first.onAvailable(old);
                first.onCapabilitiesChanged(current, caps);
                assertEquals(count, readings.size());
                monitor.start();
                ConnectivityManager.NetworkCallback next =
                        (ConnectivityManager.NetworkCallback) field(monitor, "callback");
                assertNotSame(first, next);
                next.onAvailable(current);
                next.onCapabilitiesChanged(current, caps);
                first.onLost(current);
                assertEquals(expected, readings.get(readings.size() - 1));
            } finally { monitor.stop(); }
        });
    }

    @Test public void iconIsAccessibleBesideClockWithoutAddingNavigationAndMonitorStops() {
        try (ActivityScenario<AuroraActivity> scene = ActivityScenario.launch(AuroraActivity.class)) {
            Object[] monitor = new Object[1];
            scene.onActivity(activity -> {
                monitor[0] = field(activity, "network");
                assertNotNull(field(monitor[0], "callback"));
                AuroraHomeView home = (AuroraHomeView) field(activity, "home");
                ImageView icon = activity.findViewById(R.id.network_status);
                assertNotNull(icon);
                assertFalse(icon.isFocusable());
                assertFalse(icon.isClickable());
                for (NetworkMonitor.Status status : NetworkMonitor.Status.values()) {
                    home.setNetworkStatus(status);
                    assertNotNull(icon.getDrawable());
                    assertEquals(activity.getString(status.description), icon.getContentDescription());
                }
                assertTrue(icon.getRight() < activity.findViewById(R.id.clock).getLeft());
            });
            scene.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> assertNull(field(monitor[0], "callback")));
            scene.moveToState(Lifecycle.State.RESUMED);
            scene.onActivity(activity -> assertNotNull(field(monitor[0], "callback")));
        }
    }
}
