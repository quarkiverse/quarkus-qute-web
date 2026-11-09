package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

public class ImageUtilsDigestTest {

    /**
     * 8 hex chars (32 bits): a collision only matters between two images with the same file name, since the
     * digest is the directory and the file name carries the base name, so this is plenty.
     */
    @Test
    public void testDigestIsEightHexChars() {
        String digest = ImageUtils.digest("hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(8, digest.length());
        assertTrue(digest.matches("[0-9a-f]{8}"), digest);
    }

    @Test
    public void testDigestIsStable() {
        assertEquals(ImageUtils.digest("a"), ImageUtils.digest("a"));
        assertNotEquals(ImageUtils.digest("a"), ImageUtils.digest("b"));
    }
}
