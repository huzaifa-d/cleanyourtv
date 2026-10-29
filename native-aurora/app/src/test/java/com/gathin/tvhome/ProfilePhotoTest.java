package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProfilePhotoTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void absentPrivatePhotoKeepsDefault() throws Exception {
        assertNull(ProfilePhoto.load(null));
        assertNull(ProfilePhoto.load(new File(temporary.getRoot(), "profile.jpg")));
    }

    @Test public void smallPhotosAreNotUpsampledDuringDecode() {
        assertEquals(1, ProfilePhoto.sampleSize(128, 64));
    }

    @Test public void suppliedSizeIsDecodedWithinTheBound() {
        assertEquals(2, ProfilePhoto.sampleSize(512, 512));
    }

    @Test public void largePhotosUsePowerOfTwoSampling() {
        assertEquals(16, ProfilePhoto.sampleSize(8000, 4000));
        assertEquals(16, ProfilePhoto.sampleSize(4000, 8000));
    }
}
