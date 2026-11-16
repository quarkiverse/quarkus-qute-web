package io.quarkiverse.qute.web.image.runtime;

import java.io.Serializable;
import java.util.List;

/** A preset as recorded with its tags: plain values only, so it serializes (null for an absent crop or pixel ratio). */
public record ResolvedPresetConfig(
        List<String> normalizedFormats,
        String fallbackFormat,
        List<Integer> widths,
        int quality,
        CropConfig crop,
        boolean directUrl,
        PixelRatioConfig pixelRatio) implements Serializable {

    public static ResolvedPresetConfig from(PresetConfig config) {
        return new ResolvedPresetConfig(
                config.normalizedFormats(),
                ImageUtils.normalizeFormat(config.fallbackFormat()),
                config.widths(),
                config.quality(),
                config.crop().map(c -> new CropConfig(c.ratio(), c.keep())).orElse(null),
                config.directUrl(),
                config.pixelRatio().map(pr -> new PixelRatioConfig(pr.baseWidth()))
                        .orElse(null));
    }

    /** The settings the variants of this preset carry, see {@link ImageUtils#variantSettings}. */
    public String variantSettings() {
        return ImageUtils.variantSettings(crop != null ? crop.ratio() : null, crop != null ? crop.keep() : null,
                quality);
    }

    public record CropConfig(String ratio, PresetConfig.Keep keep) implements Serializable {
    }

    public record PixelRatioConfig(int baseWidth) implements Serializable {
    }
}
