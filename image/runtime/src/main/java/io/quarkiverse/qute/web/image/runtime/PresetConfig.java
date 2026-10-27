package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface PresetConfig {

    PresetConfig DEFAULT = new DefaultPresetConfig();

    /**
     * Output formats in preference order (e.g., webp, jpg).
     */
    @WithDefault("webp,jpg")
    List<String> formats();

    default List<String> normalizedFormats() {
        return formats().stream().map(ImageUtils::normalizeFormat).toList();
    }

    /**
     * Fallback format for the {@literal <img>} element.
     */
    @WithDefault("jpg")
    String fallbackFormat();

    /**
     * Target widths for responsive srcsets (pixel-based).
     */
    List<Integer> widths();

    /**
     * Overall quality for generated images (0-100).
     */
    @WithDefault("100")
    Integer quality();

    /**
     * When true, output the direct image URL only (no {@literal <picture>}/{@literal <img>} markup).
     */
    @WithDefault("false")
    boolean directUrl();

    /**
     * Crop aspect ratio (e.g., "1:1", "4:3") and which part of the image to keep when cropping.
     */
    Optional<Crop> crop();

    /**
     * Pixel density multipliers for multiplier srcset (e.g., [1, 1.5, 2]).
     * When set, srcset uses "Nx" descriptors instead of "Nw".
     */
    Optional<PixelRatio> pixelRatio();

    interface Crop {
        /**
         * Aspect ratio for cropping (e.g., "16:9", "1:1", "4:3").
         */
        String ratio();

        /**
         * Which part of the image to keep when cropping.
         */
        @WithDefault("center")
        Keep keep();
    }

    enum Keep {
        NONE,
        ALL,
        ENTROPY,
        CENTER,
        ATTENTION,
        LOW,
        HIGH,
    }

    interface PixelRatio {
        /**
         * Base width in pixels for the 1x variant.
         */
        int baseWidth();

        /**
         * Width of the fallback image (used in the src attribute).
         */
        int fallbackWidth();

        /**
         * Pixel density multipliers (e.g., 1, 1.5, 2).
         */
        List<Double> ratios();
    }
}
