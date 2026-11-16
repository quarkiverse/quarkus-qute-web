package io.quarkiverse.qute.web.image.converter;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;

public record ImageOptions(
        int width,
        int height,
        String outputFormat,
        CropOptions crop,
        int quality) {

    public record CropOptions(String ratio, PresetConfig.Keep keep) {
    }
}
