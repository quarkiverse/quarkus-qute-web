package io.quarkiverse.qute.web.image.converter;

import java.nio.file.Path;

public interface ImageConverter {

    /**
     * Read an image, detect its format and dimensions.
     */
    ImageInfo readInfo(byte[] sourceImage);

    /**
     * Decode the whole image, for bytes that should not be trusted on their header alone (a download).
     *
     * @throws UnsupportedFormatException when the bytes cannot be decoded
     */
    void decode(byte[] sourceImage);

    /**
     * Process an image: resize, optionally crop, convert format, and write to the output path.
     */
    void process(byte[] sourceImage, ImageOptions options, Path outputPath);

    /**
     * Check if this converter supports writing the given output format (e.g. "jpg", "webp").
     */
    default boolean supportsFormat(String outputFormat) {
        return true;
    }

    /**
     * A short stable id of this converter. It is hashed into the generated file names (never visible as is),
     * so two converters never share a cached variant since their output differs for the same settings.
     */
    String id();

}
