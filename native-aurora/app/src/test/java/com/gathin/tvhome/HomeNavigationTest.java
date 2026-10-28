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
        assertEquals(4, navigation.move(HomeNavigation.Direction.LEFT));
        assertEquals(2, navigation.move(HomeNavigation.Direction.UP));
        assertEquals(HomeNavigation.PROFILE, navigation.move(HomeNavigation.Direction.UP));
        assertEquals(2, navigation.move(HomeNavigation.Direction.DOWN));
    }

    @Test public void rowEdgesDoNotWrap() {
        HomeNavigation navigation = new HomeNavigation();
        navigation.select(0);
        assertEquals(0, navigation.move(HomeNavigation.Direction.LEFT));
        navigation.select(6);
        assertEquals(6, navigation.move(HomeNavigation.Direction.RIGHT));
        assertEquals(6, navigation.move(HomeNavigation.Direction.DOWN));
        navigation.select(3);
        assertEquals(3, navigation.move(HomeNavigation.Direction.RIGHT));
        navigation.select(5);
        assertEquals(5, navigation.move(HomeNavigation.Direction.LEFT));
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
        navigation.select(5);
        assertEquals(4, navigation.move(HomeNavigation.Direction.RIGHT));
        assertEquals(6, navigation.move(HomeNavigation.Direction.RIGHT));
        assertEquals(3, navigation.move(HomeNavigation.Direction.UP));
        navigation.move(HomeNavigation.Direction.UP);
        assertEquals(3, navigation.move(HomeNavigation.Direction.DOWN));
        HomeNavigation restored = new HomeNavigation();
        restored.restore(HomeNavigation.IDLE, 6);
        restored.move(HomeNavigation.Direction.UP);
        assertEquals(6, restored.move(HomeNavigation.Direction.DOWN));
    }

    @Test public void twoRowsUseNearestColumnWithoutHorizontalWrapping() {
        HomeNavigation navigation = new HomeNavigation();
        int[] down = {5, 5, 4, 6};
        for (int i = 0; i < down.length; i++) {
            navigation.select(i);
            assertEquals(down[i], navigation.move(HomeNavigation.Direction.DOWN));
        }
        int[] up = {2, 0, 3};
        for (int i = 0; i < up.length; i++) {
            navigation.select(i + 4);
            assertEquals(up[i], navigation.move(HomeNavigation.Direction.UP));
            assertEquals(HomeNavigation.PROFILE, navigation.move(HomeNavigation.Direction.UP));
        }
    }
}
