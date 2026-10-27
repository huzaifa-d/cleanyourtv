package com.gathin.tvhome;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeNavigationTest {
    @Test public void recreationPreservesIdleAndLastRowWithoutPersistingColdFocus() {
        HomeNavigation restored = new HomeNavigation();
        restored.restore(HomeNavigation.IDLE, 5);
        assertEquals(HomeNavigation.IDLE, restored.target());
        restored.move(HomeNavigation.Direction.UP);
        assertEquals(5, restored.move(HomeNavigation.Direction.DOWN));
        HomeNavigation cold = new HomeNavigation();
        assertEquals(HomeNavigation.IDLE, cold.target());
        cold.move(HomeNavigation.Direction.UP);
        assertEquals(0, cold.move(HomeNavigation.Direction.DOWN));
    }
    @Test public void startsIdleAndEntersFromEitherEnd() {
        HomeNavigation navigation = new HomeNavigation();
        assertEquals(HomeNavigation.IDLE, navigation.target());
        assertEquals(0, navigation.move(HomeNavigation.Direction.RIGHT));
        navigation.clear();
        assertEquals(6, navigation.move(HomeNavigation.Direction.LEFT));
        assertEquals(5, navigation.move(HomeNavigation.Direction.LEFT));
        assertEquals(HomeNavigation.PROFILE, navigation.move(HomeNavigation.Direction.UP));
        assertEquals(5, navigation.move(HomeNavigation.Direction.DOWN));
    }

    @Test public void rowEdgesDoNotWrap() {
        HomeNavigation navigation = new HomeNavigation();
        navigation.select(0);
        assertEquals(0, navigation.move(HomeNavigation.Direction.LEFT));
        navigation.select(6);
        assertEquals(6, navigation.move(HomeNavigation.Direction.RIGHT));
        assertEquals(6, navigation.move(HomeNavigation.Direction.DOWN));
    }

    @Test public void backClearsSelectionButProfileRemembersRow() {
        HomeNavigation navigation = new HomeNavigation();
        navigation.select(3);
        navigation.clear();
        assertEquals(HomeNavigation.IDLE, navigation.target());
        navigation.move(HomeNavigation.Direction.UP);
        assertEquals(3, navigation.move(HomeNavigation.Direction.DOWN));
        assertThrows(IllegalArgumentException.class, () -> navigation.select(7));
    }

    @Test public void appleTvAndAllAppsAreSeparateReachableTargets() {
        HomeNavigation navigation = new HomeNavigation();
        navigation.select(4);
        assertEquals(5, navigation.move(HomeNavigation.Direction.RIGHT));
        assertEquals(6, navigation.move(HomeNavigation.Direction.RIGHT));
        navigation.move(HomeNavigation.Direction.UP);
        assertEquals(6, navigation.move(HomeNavigation.Direction.DOWN));
        HomeNavigation restored = new HomeNavigation();
        restored.restore(HomeNavigation.IDLE, 6);
        restored.move(HomeNavigation.Direction.UP);
        assertEquals(6, restored.move(HomeNavigation.Direction.DOWN));
    }
}
