package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class NetworkStatusTest {
    @Test public void connectedButUnvalidatedOrLocalOnlyIsOffline() {
        assertEquals(NetworkMonitor.Status.OFFLINE, NetworkMonitor.Status.from(true, false, false, true));
        assertEquals(NetworkMonitor.Status.OFFLINE, NetworkMonitor.Status.from(true, false, true, false));
        assertEquals(NetworkMonitor.Status.OFFLINE, NetworkMonitor.Status.from(false, true, true, false));
        assertEquals(NetworkMonitor.Status.OFFLINE, NetworkMonitor.Status.from(false, false, false, false));
    }

    @Test public void validatedInternetUsesActiveTransportWithoutCallingOtherTransportOffline() {
        assertEquals(NetworkMonitor.Status.ETHERNET, NetworkMonitor.Status.from(true, true, true, false));
        assertEquals(NetworkMonitor.Status.WIFI, NetworkMonitor.Status.from(true, true, false, true));
        assertEquals(NetworkMonitor.Status.ETHERNET, NetworkMonitor.Status.from(true, true, true, true));
        assertEquals(NetworkMonitor.Status.OTHER, NetworkMonitor.Status.from(true, true, false, false));
    }
}
