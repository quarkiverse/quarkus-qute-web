package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Optional;

public record DefaultPresetConfig() implements PresetConfig {

    @Override
    public List<String> formats() {
        return List.of("webp", "jpg");
    }

    @Override
    public String fallbackFormat() {
        return "jpg";
    }

    @Override
    public List<Integer> widths() {
        return List.of(640, 1024, 1920, 2560);
    }

    @Override
    public Integer quality() {
        return 100;
    }

    @Override
    public boolean directUrl() {
        return false;
    }

    @Override
    public Optional<Crop> crop() {
        return Optional.empty();
    }

    @Override
    public Optional<PixelRatio> pixelRatio() {
        return Optional.empty();
    }

}
