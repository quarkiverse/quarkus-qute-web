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

    @Test
    public void testOriginalAndSlugify() {
        assertEquals("/static/images/generated/abcd1234/foe-photo-original.jpg",
                ImageUtils.computeOutputPath("/static/images/generated", new ImageId("abcd1234", "Foé photo", "jpg"),
                        null, "h", "jpg", true));
    }
}
