package io.quarkiverse.qute.web.image.converter;

public record ImageOptions(
        int width,
        int height,
        String outputFormat,
        CropOptions crop,
        int quality) {

    public record CropOptions(String ratio, CropPosition position) {
    }

    public enum CropPosition {
        CENTER,
        TOP,
        BOTTOM,
    }
}
