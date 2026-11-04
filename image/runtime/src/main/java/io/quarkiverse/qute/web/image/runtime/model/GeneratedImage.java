package io.quarkiverse.qute.web.image.runtime.model;

public record GeneratedImage(
        ImageId original,
        int width,
        int height,
        String format,
        String extension,
        String hash,
        String outputPath) {
}
