package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.ImageTemplateExtension;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;

/** What the tag renders from a processed image: the fallback source, the picture sources, the descriptors. */
public class ImageTemplateExtensionTest {

    static final ImageId ID = new ImageId("abc123", "photo", "jpg");
    static final String SETTINGS = ImageUtils.variantSettings(null, null, 80);

    static GeneratedImage variant(String format, int width) {
        String ext = ImageUtils.extensionFromFormat(format);
        return new GeneratedImage(ID, width, width / 2, format, ext, "h", "/gen/abc123/photo-" + width + "-h." + ext,
                SETTINGS);
    }

    static ImageTag tag(ResolvedPresetConfig preset, GeneratedImage... variants) {
        Image image = new Image(ID, new OriginalInfo("jpeg", 1920, 960), List.of(variants), false);
        return new ImageTag("page.html", "/photo.jpg", preset, image, "/root", "/root/photo.jpg");
    }

    @Test
    public void testTwoFormatsMakeAPictureWithTheFallbackAsImg() {
        ResolvedPresetConfig preset = new ResolvedPresetConfig(List.of("image/webp", "image/jpeg"), "image/jpeg",
                List.of(320, 640), 80, null, false, null);
        ImageTag tag = tag(preset, variant("image/webp", 320), variant("image/webp", 640),
                variant("image/jpeg", 320), variant("image/jpeg", 640));

        assertTrue(ImageTemplateExtension.isPicture(tag));
        assertEquals("/root/gen/abc123/photo-640-h.jpg", ImageTemplateExtension.imgSrc(tag), "the largest fallback");
        List<ImageTemplateExtension.Source> sources = ImageTemplateExtension.sources(tag);
        assertEquals(List.of("image/webp", "image/jpeg"), sources.stream().map(ImageTemplateExtension.Source::type).toList());
        assertEquals("/root/gen/abc123/photo-320-h.webp 320w, /root/gen/abc123/photo-640-h.webp 640w",
                sources.get(0).srcset());
        assertEquals("", ImageTemplateExtension.imgSrcset(tag), "srcset belongs to the sources when there are two");
    }

    @Test
    public void testOneFormatIsAnImgWithSrcset() {
        ResolvedPresetConfig preset = new ResolvedPresetConfig(List.of("image/jpeg"), "image/jpeg", List.of(320, 640),
                80, null, false, null);
        ImageTag tag = tag(preset, variant("image/jpeg", 320), variant("image/jpeg", 640));

        assertFalse(ImageTemplateExtension.isPicture(tag));
        assertEquals("/root/gen/abc123/photo-320-h.jpg 320w, /root/gen/abc123/photo-640-h.jpg 640w",
                ImageTemplateExtension.imgSrcset(tag));
    }

    @Test
    public void testPixelRatioDescriptorsAreTrimmed() {
        ResolvedPresetConfig preset = new ResolvedPresetConfig(List.of("image/jpeg"), "image/jpeg",
                List.of(160, 240, 320), 80, null, false, new ResolvedPresetConfig.PixelRatioConfig(160));
        ImageTag tag = tag(preset, variant("image/jpeg", 160), variant("image/jpeg", 240), variant("image/jpeg", 320));

        assertEquals("/root/gen/abc123/photo-160-h.jpg 1x, /root/gen/abc123/photo-240-h.jpg 1.5x,"
                + " /root/gen/abc123/photo-320-h.jpg 2x", ImageTemplateExtension.imgSrcset(tag));
    }

    @Test
    public void testOtherPresetsVariantsAreIgnored() {
        ResolvedPresetConfig preset = new ResolvedPresetConfig(List.of("image/jpeg"), "image/jpeg", List.of(320), 80,
                null, false, null);
        GeneratedImage cropped = new GeneratedImage(ID, 320, 320, "image/jpeg", "jpg", "c", "/gen/abc123/photo-320-c.jpg",
                ImageUtils.variantSettings("1:1", null, 80));
        ImageTag tag = tag(preset, variant("image/jpeg", 320), cropped);

        assertEquals("/root/gen/abc123/photo-320-h.jpg", ImageTemplateExtension.imgSrc(tag));
    }

    @Test
    public void testPassthroughRendersTheOriginal() {
        ResolvedPresetConfig preset = new ResolvedPresetConfig(List.of("image/jpeg"), "image/jpeg", List.of(320), 80,
                null, false, null);
        Image image = new Image(ID, new OriginalInfo("svg", 0, 0), List.of(), true);
        ImageTag tag = new ImageTag("page.html", "/logo.svg", preset, image, "/root", "/root/logo.svg");

        assertEquals("/root/logo.svg", ImageTemplateExtension.imgSrc(tag));
        assertTrue(ImageTemplateExtension.sources(tag).isEmpty());
        assertTrue(ImageTemplateExtension.bestVariant(tag).isEmpty());
    }
}
