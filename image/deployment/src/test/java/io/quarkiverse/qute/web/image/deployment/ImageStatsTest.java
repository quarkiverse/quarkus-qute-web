package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.deployment.items.model.ImageStats;

/** The summary line and its units. */
public class ImageStatsTest {

    @Test
    public void testUnits() {
        assertEquals("1 KB", ImageStats.bytes(1));
        assertEquals("184 KB", ImageStats.bytes(184 * 1024));
        assertEquals("5.2 MB", ImageStats.bytes(5_452_595));
        assertEquals("312 ms", ImageStats.millis(312));
        assertEquals("4.1 s", ImageStats.millis(4_100));
    }

    @Test
    public void testSummary() {
        ImageStats stats = new ImageStats();
        stats.downloaded(5_452_595, 4_100);
        stats.generated("/a-320.jpg", 12_000_000, 7_950);
        stats.generated("/a-640.jpg", 1_000_000, 50);
        stats.reused("/b-320.jpg");
        stats.reused("/a-320.jpg"); // written by this build: a second request, not a reuse
        stats.passedThrough();
        stats.passedThrough();
        assertEquals("Images: 34 sources (1 downloaded, 5.2 MB in 4.1 s), 2 variants generated (12.4 MB in 8.0 s),"
                + " 1 reused, 2 passed through", stats.summary(34));
    }

    @Test
    public void testWarmBuildSummaryHasNoTimes() {
        ImageStats stats = new ImageStats();
        stats.reused("/b-320.jpg");
        assertEquals("Images: 1 sources (0 downloaded), 0 variants generated, 1 reused, 0 passed through",
                stats.summary(1));
    }
}
