package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BackgroundFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void resolvesOnlyRegularDirectChildVideos() throws Exception {
        File root = temporary.newFolder("backgrounds");
        File valid = new File(root, "Blue Bokeh.mp4");
        assertTrue(valid.createNewFile());
        BackgroundFiles files = new BackgroundFiles(root);
        assertEquals(valid.getCanonicalFile(), files.resolve(valid.getName()));
        assertNull(files.resolve("../other.mp4"));
        assertNull(files.resolve("nested/movie.mp4"));
        assertNull(files.resolve("missing.mp4"));
    }

    @Test public void listsOnlyCompleteFilesInStableOrder() throws Exception {
        File root = temporary.newFolder("videos");
        assertTrue(new File(root, "z.mp4").createNewFile());
        assertTrue(new File(root, "A.MP4").createNewFile());
        assertTrue(new File(root, "copy.mp4.part").createNewFile());
        assertTrue(new File(root, "folder.mp4").mkdir());
        List<File> videos = new BackgroundFiles(root).list();
        assertEquals(2, videos.size());
        assertEquals("A.MP4", videos.get(0).getName());
        assertEquals("z.mp4", videos.get(1).getName());
    }

    @Test public void unavailableDirectoryIsEmpty() {
        assertTrue(new BackgroundFiles(null).list().isEmpty());
        assertNull(new BackgroundFiles(null).resolve("video.mp4"));
    }

    @Test public void rejectsLinkOutsideDirectoryWhenSupported() throws Exception {
        File root = temporary.newFolder("links");
        File outside = temporary.newFile("outside.mp4");
        try {
            Files.createSymbolicLink(new File(root, "linked.mp4").toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            org.junit.Assume.assumeNoException(unavailable);
        }
        assertNull(new BackgroundFiles(root).resolve("linked.mp4"));
    }
}
