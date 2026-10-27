package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import java.util.function.Consumer;

final class NetworkMonitor {
    enum Status {
        OFFLINE(R.drawable.aurora_network_offline, R.string.network_offline),
        ETHERNET(R.drawable.aurora_network_ethernet, R.string.network_ethernet),
        WIFI(R.drawable.aurora_network_wifi, R.string.network_wifi),
        OTHER(R.drawable.aurora_network_online, R.string.network_online);

        final int drawable, description;
        Status(int drawable, int description) {
            this.drawable = drawable;
            this.description = description;
        }
        static Status from(boolean internet, boolean validated, boolean ethernet, boolean wifi) {
            if (!internet || !validated) return OFFLINE;
            if (ethernet) return ETHERNET;
            return wifi ? WIFI : OTHER;
        }
    }

    private final ConnectivityManager manager;
    private final Handler main;
    private final Consumer<Status> changed;
    private ConnectivityManager.NetworkCallback callback;

    NetworkMonitor(Context context, Handler main, Consumer<Status> changed) {
        manager = context.getSystemService(ConnectivityManager.class);
        this.main = main;
        this.changed = changed;
    }

    static Status status(NetworkCapabilities capabilities) {
        return capabilities == null ? Status.OFFLINE : Status.from(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }

    void start() {
        if (callback != null) return;
        changed.accept(Status.OFFLINE);
        if (manager == null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            private Network current;
            @Override public void onAvailable(Network network) {
                if (callback != this) return;
                current = network;
                changed.accept(Status.OFFLINE);
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                if (callback == this && network.equals(current)) changed.accept(status(capabilities));
            }
            @Override public void onLost(Network network) {
                if (callback != this || !network.equals(current)) return;
                current = null;
                changed.accept(Status.OFFLINE);
            }
        };
        manager.registerDefaultNetworkCallback(callback, main);
    }

    void stop() {
        ConnectivityManager.NetworkCallback old = callback;
        callback = null;
        if (old != null) manager.unregisterNetworkCallback(old);
    }
}
