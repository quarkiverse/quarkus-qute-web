package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface PresetConfig {


    PresetConfig DEFAULT = new DefaultPresetConfig();

    /**
     * Output formats in preference order (e.g., webp, jpg).
     */
    @WithDefault("webp,jpg")
    List<String> formats();

    /**
     * Fallback format for the <img> element.
     * Example: "jpg".
     */
    @WithDefault("jpg")
    String fallbackFormat();

    /**
     * Target widths for responsive srcsets (pixel-based).
     * Not used for pixel-ratio presets (see baseWidth + pixelRatios).
     */
    List<Integer> widths();

    /**
     * Overall quality for generated images (0-100).
     */
    Integer quality();

    /**
     * Markup mode controlling how attributes are emitted:
     * - "auto" : standard <picture> markup.
     * - "data_auto" : data-* attributes for lazy libraries.
     * - "data_img" : emit only <img> with data-* attributes.
     * - "direct_url" : return direct URL only (no <picture>/<img>).
     * <p>
     * Note: if absent, default to "auto" in your service.
     */
    @WithDefault("auto")
    MarkupMode markup();

    enum MarkupMode {
        AUTO,
        DATA_AUTO,
        DATA_IMG,
        DIRECT_URL
    }

    /**
     * Whether to include a <noscript> fallback block.
     */
    @WithDefault("false")
    Boolean noscript();

    /**
     * Optional HTML attributes per generated tag (quick win).
     * Keys typically: "picture", "img", "parent", "a".
     * Values are raw attribute strings (e.g., 'class="lazy"').
     */
    Map<String, String> attributes();

    /**
     * Crop aspect ratio (e.g., "1:1", "4:3"). Optional.
     */
    Optional<Crop> crop();

    /**
     * Base width (in px) for pixel-ratio presets (used with pixelRatios).
     * Example: 80, 48, 150.
     * Pixel density multipliers (e.g., [1, 1.5, 2]) for multiplier srcset.
     * Used when baseWidth is set.
     */
    Optional<PixelRatio> pixelRatio();

    // ---- Commented for future evolutions ----
    // Map<String, Integer> formatQuality();      // Per-format quality overrides (webp/avif/jp2)
    // Boolean stripMetadata();                   // Remove EXIF/ICC
    // Map<String, Map<String, String>> imageOptions(); // Per-format encoder options
    // Boolean linkSource();                      // Wrap image with original
    // Boolean dimensionAttributes();             // Add width/height to prevent CLS
    // Map<String, String> sizes();               // Conditional sizes by media query key
    // String size();                             // Unconditional size ("800px")

    record Crop(String ratio, String keep) {
    }

    record PixelRatio(int baseWidth, int fallbackWidth, List<Double> ratios) {

    }
}
