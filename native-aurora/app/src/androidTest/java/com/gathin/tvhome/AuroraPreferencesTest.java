package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuroraPreferencesTest {
    private final SharedPreferences store = InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getSharedPreferences("preference-test", Context.MODE_PRIVATE);

    @After public void cleanup() { store.edit().clear().commit(); }

    @Test public void choicesSurviveRecreation() {
        store.edit().clear().commit();
        AuroraPreferences first = new AuroraPreferences(store);
        assertEquals(0.5f, first.speed(), 0f);
        assertTrue(first.showRam());
        first.setVideo("my background.mp4");
        first.setSpeed(1.75f);
        first.setShowRam(false);
        AuroraPreferences second = new AuroraPreferences(store);
        assertEquals("my background.mp4", second.video());
        assertEquals(1.75f, second.speed(), 0f);
        assertFalse(second.showRam());
    }

    @Test public void rejectsUnsafeNamesAndInvalidSpeeds() {
        AuroraPreferences preferences = new AuroraPreferences(store);
        assertThrows(IllegalArgumentException.class, () -> preferences.setVideo("../escape.mp4"));
        assertThrows(IllegalArgumentException.class, () -> preferences.setSpeed(0.3f));
        assertThrows(IllegalArgumentException.class, () -> preferences.setSpeed(Float.NaN));
        store.edit().putFloat("speed", 99f).commit();
        assertEquals(0.5f, preferences.speed(), 0f);
    }
}
