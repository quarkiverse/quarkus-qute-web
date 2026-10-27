package io.quarkiverse.qute.web.image.converter;

import java.nio.file.Path;

public interface ImageConverter {

    /**
     * Read an image, detect its format and dimensions.
     */
    ImageInfo readInfo(byte[] sourceImage);

    /**
     * Process an image: resize, optionally crop, convert format, and write to the output path.
     *
     * @return the output path (same as provided)
     */
    Path process(byte[] sourceImage, ImageOptions options, Path outputPath);

    /**
     * Check if this converter supports writing the given output format (e.g. "jpg", "webp").
     */
    default boolean supportsFormat(String outputFormat) {
        return true;
    }

}
