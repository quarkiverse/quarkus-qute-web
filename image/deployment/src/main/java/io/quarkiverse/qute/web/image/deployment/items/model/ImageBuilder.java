package io.quarkiverse.qute.web.image.deployment.items.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;

public class ImageBuilder {
    private final ImageId id;
    private OriginalInfo info;
    private final Map<String, GeneratedImage> generatedMap = new HashMap<>();

    public ImageBuilder(ImageId id) {
        this.id = id;
    }

    public Map<String, GeneratedImage> generatedMap() {
        return generatedMap;
    }

    public ImageBuilder addGeneratedImage(GeneratedImageOptions options, boolean slugifyOutput,
            Consumer<GeneratedImage> consumer) {
        String hash = ImageUtils.digest("%s-%s".formatted(id.toString(), options.settings()));
        final String generatedKey = options.width() + "-" + hash;
        generatedMap.computeIfAbsent(generatedKey, key -> {
            final String outputExt = ImageUtils.getOutputExt(id, options.format());
            final String outputPath = ImageUtils.computeOutputPath(id, options.width(), hash, outputExt, slugifyOutput);
            final GeneratedImage generatedImage = new GeneratedImage(id, options.width(), options.height(),
                    options.format(), outputExt, hash, outputPath);
            consumer.accept(generatedImage);
            return generatedImage;
        });
        return this;
    }

    public ImageBuilder info(OriginalInfo info) {
        this.info = info;
        return this;
    }

    public Image build() {
        return new Image(id, info, List.copyOf(generatedMap.values()));
    }
}
