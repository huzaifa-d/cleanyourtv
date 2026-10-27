package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class AppCatalogTest {
    @Test public void keepsTvEntryBeforePhoneEntryAndExcludesSelf() {
        Map<String, String> merged = new LinkedHashMap<>();
        AppCatalog.preferFirst(merged, "self", "video", "leanback");
        AppCatalog.preferFirst(merged, "self", "video", "phone");
        AppCatalog.preferFirst(merged, "self", "self", "launcher");
        assertEquals(1, merged.size());
        assertEquals("leanback", merged.get("video"));
    }
}
