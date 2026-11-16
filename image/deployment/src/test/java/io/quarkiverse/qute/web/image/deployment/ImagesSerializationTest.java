package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;

/**
 * The processed maps are recorded as a serialized resource, not as bytecode constants: a round trip keeps tags and
 * images, including a preset with a crop and one without.
 */
public class ImagesSerializationTest {

    @Test
    public void testRoundTrip() {
        ImageId id = new ImageId("abc123", "photo", "jpg");
        GeneratedImage variant = new GeneratedImage(id, 320, 200, "jpeg", "jpg", "h1",
                "/static/images/generated/abc123/photo_320.jpg",
                "q80");
        Image image = new Image(id, new OriginalInfo("jpeg", 1920, 1080), List.of(variant), false);
        ResolvedPresetConfig cropped = new ResolvedPresetConfig(List.of("jpeg"), "jpeg", List.of(320), 80,
                new ResolvedPresetConfig.CropConfig("16:9", PresetConfig.Keep.CENTER), false, null);
        ResolvedPresetConfig plain = new ResolvedPresetConfig(List.of("jpeg"), "jpeg", List.of(320), 80,
                null, true, new ResolvedPresetConfig.PixelRatioConfig(160));
        ImageTag tag = new ImageTag("page.html", "/photo.jpg", cropped, image, "", "/photo.jpg");
        ImageTag globTag = new ImageTag("*", "/photo.jpg", plain, image, "/root", "/root/photo.jpg");

        Images images = new Images(Map.of("page.html|/photo.jpg|hero", tag, "*|/photo.jpg|default", globTag),
                List.of("html", "txt"));

        Images read = Images.read(new ByteArrayInputStream(images.toBytes()));

        assertEquals(tag, read.get("page.html", "/photo.jpg", "hero"));
        assertEquals(tag, read.get("page", "/photo.jpg", "hero"), "a template reloaded in dev mode drops its suffix");
        assertEquals(globTag, read.get("other.html", "/photo.jpg", "default"));
        assertNull(read.get("page.html", "/photo.jpg", "nope"));
    }
}
