package io.quarkiverse.qute.web.image.runtime.model;

import java.io.Serializable;

/**
 * A variant of an image.
 *
 * @param settings the preset settings it was produced with (crop and quality, see
 *        {@code ResolvedPresetConfig.variantSettings()}): variants of one image are shared by every preset using it,
 *        and a preset only renders its own
 */
public record GeneratedImage(
        ImageId original,
        int width,
        int height,
        String format,
        String extension,
        String hash,
        String outputPath,
        String settings) implements Serializable {
}
