package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

public class ImageUtilsDigestTest {

    /** The full SHA-1: it keys images and cached downloads, where a collision would silently swap two images. */
    @Test
    public void testDigestIsTheFullHash() {
        String digest = ImageUtils.digest("hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(digest.matches("[0-9a-f]{40}"), digest);
    }

    @Test
    public void testDigestIsStable() {
        assertEquals(ImageUtils.digest("a"), ImageUtils.digest("a"));
        assertNotEquals(ImageUtils.digest("a"), ImageUtils.digest("b"));
    }
}
