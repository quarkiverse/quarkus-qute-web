package io.quarkiverse.qute.web.image.dynamic.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageIOConverter;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class DynamicImageRecorder {

    public Supplier<DynamicImageRuntimeConfig> createConfig(
            List<ImageDir> imageDirs,
            Map<String, ResolvedPresetConfig> presets,
            boolean useVips,
            boolean slugifyOutput) {
        return () -> {
            ImageConverter converter = createConverter(useVips);
            Path cacheDir;
            try {
                cacheDir = Files.createTempDirectory("qute-image-dynamic");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create dynamic image cache directory", e);
            }
            return new DynamicImageRuntimeConfig(converter, imageDirs, presets, cacheDir, slugifyOutput);
        };
    }

    public Handler<RoutingContext> createRouteHandler() {
        return new DynamicImageRouteHandler();
    }

    private static ImageConverter createConverter(boolean useVips) {
        if (useVips) {
            try {
                Class<?> vipsClass = Class.forName("io.quarkiverse.qute.web.image.converter.vips.VipsConverter");
                return (ImageConverter) vipsClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // fall through to ImageIO
            }
        }
        return new ImageIOConverter();
    }
}
