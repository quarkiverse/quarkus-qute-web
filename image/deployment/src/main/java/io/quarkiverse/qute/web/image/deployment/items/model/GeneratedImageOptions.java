package io.quarkiverse.qute.web.image.deployment.items.model;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;

/**
 * Everything that changes the bytes of a generated variant for a given source and width. {@link #settings()} is
 * hashed into the output file name, so a change in any of these produces a new file instead of reusing a stale one.
 */
public record GeneratedImageOptions(int width, int height, String format, PresetConfig.Crop crop, int quality,
        String converterId) {

    public String settings() {
        return variantSettings() + "-" + converterId;
    }

    /** The settings a preset can recompute at render time, see {@link GeneratedImage#settings()}. */
    public String variantSettings() {
        return ImageUtils.variantSettings(crop == null ? null : crop.ratio(), crop == null ? null : crop.keep(),
                quality);
    }

}
