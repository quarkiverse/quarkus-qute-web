package io.quarkiverse.qute.web.image.dynamic.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;

public class DynamicImageRuntimeConfig {

    private final ImageConverter converter;
    private final List<ImageDir> imageDirs;
    private final Map<String, ResolvedPresetConfig> presets;
    private final Path cacheDir;
    private final boolean slugifyOutput;

    public DynamicImageRuntimeConfig(ImageConverter converter, List<ImageDir> imageDirs,
            Map<String, ResolvedPresetConfig> presets, Path cacheDir, boolean slugifyOutput) {
        this.converter = converter;
        this.imageDirs = imageDirs;
        this.presets = presets;
        this.cacheDir = cacheDir;
        this.slugifyOutput = slugifyOutput;
    }

    public ImageConverter converter() {
        return converter;
    }

    public List<ImageDir> imageDirs() {
        return imageDirs;
    }

    public Map<String, ResolvedPresetConfig> presets() {
        return presets;
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public boolean slugifyOutput() {
        return slugifyOutput;
    }
}
