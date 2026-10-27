package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Optional;

public record ResolvedPresetConfig(
        List<String> formats,
        List<String> normalizedFormats,
        String fallbackFormat,
        List<Integer> widths,
        boolean directUrl,
        Optional<PixelRatioConfig> pixelRatio) {

    public static ResolvedPresetConfig from(PresetConfig config) {
        return new ResolvedPresetConfig(
                config.formats(),
                config.normalizedFormats(),
                ImageUtils.normalizeFormat(config.fallbackFormat()),
                config.widths(),
                config.directUrl(),
                config.pixelRatio().map(pr -> new PixelRatioConfig(pr.baseWidth(), pr.fallbackWidth(), pr.ratios())));
    }

    public record PixelRatioConfig(int baseWidth, int fallbackWidth, List<Double> ratios) {
    }
}
