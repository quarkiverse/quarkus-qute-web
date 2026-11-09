package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;

public class GeneratedImageOptionsTest {

    @Test
    public void testSettingsIncludeConverter() {
        GeneratedImageOptions a = new GeneratedImageOptions(100, 50, "jpg", null, 80, "imageio");
        assertEquals("80-imageio", a.settings());
    }

    @Test
    public void testQualityChangesSettings() {
        GeneratedImageOptions a = new GeneratedImageOptions(100, 50, "jpg", null, 80, "imageio");
        GeneratedImageOptions b = new GeneratedImageOptions(100, 50, "jpg", null, 70, "imageio");
        assertNotEquals(a.settings(), b.settings());
    }

    @Test
    public void testConverterChangesSettings() {
        GeneratedImageOptions a = new GeneratedImageOptions(100, 50, "jpg", null, 80, "imageio");
        GeneratedImageOptions b = new GeneratedImageOptions(100, 50, "jpg", null, 80, "vips");
        assertNotEquals(a.settings(), b.settings());
    }
}
