package io.quarkiverse.qute.web.image.deployment.items.model;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;

public record GeneratedImageOptions(int width, int height, String format, PresetConfig.Crop crop, int quality) {

    public String settings() {
        return (crop == null ? "" : crop.ratio() + "-" + crop.keep() + "-") + quality;
    }

}
