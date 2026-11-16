package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;

public class ImageUtilsOutputPathTest {

    @Test
    public void testConfiguredPrefix() {
        assertEquals("/img/gen/abcd1234/photo-640-h.jpg",
                ImageUtils.computeOutputPath("/img/gen", new ImageId("abcd1234", "photo", "jpg"), 640, "h", "jpg",
                        false));
    }

    /** The base name is slugified when asked, and the directory is the short digest of a full one. */
    @Test
    public void testSlugifyAndShortDigest() {
        assertEquals("/static/images/generated/abcd1234/foe-photo-640-h.jpg",
                ImageUtils.computeOutputPath("/static/images/generated",
                        new ImageId("abcd1234ffffffffffffffffffffffffffffffff", "Foé photo", "jpg"), 640, "h", "jpg", true));
    }
}
