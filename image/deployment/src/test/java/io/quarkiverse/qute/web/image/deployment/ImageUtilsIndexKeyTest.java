package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

public class ImageUtilsIndexKeyTest {

    @Test
    public void testWithPrefix() {
        assertEquals("web/images/photo.png", ImageUtils.indexKey("web", "images/photo.png"));
    }

    @Test
    public void testWithoutPrefix() {
        assertEquals("images/photo.png", ImageUtils.indexKey(null, "images/photo.png"));
        assertEquals("images/photo.png", ImageUtils.indexKey("", "images/photo.png"));
    }

    @Test
    public void testStripsLeadingSlash() {
        assertEquals("images/photo.png", ImageUtils.indexKey(null, "/images/photo.png"));
        assertEquals("web/images/photo.png", ImageUtils.indexKey("web", "/images/photo.png"));
    }

    @Test
    public void testNormalizesUnicode() {
        String nfd = Normalizer.normalize("Foé.png", Normalizer.Form.NFD);
        String nfc = Normalizer.normalize("Foé.png", Normalizer.Form.NFC);

        assertEquals(ImageUtils.indexKey(null, nfc), ImageUtils.indexKey(null, nfd));
        assertEquals(ImageUtils.indexKey("web", nfc), ImageUtils.indexKey("web", nfd));
    }
}
