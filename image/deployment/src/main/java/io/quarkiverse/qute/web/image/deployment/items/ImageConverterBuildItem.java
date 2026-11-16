package io.quarkiverse.qute.web.image.deployment.items;

import java.util.function.Supplier;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ImageConverterBuildItem extends SimpleBuildItem {

    private final Supplier<ImageConverter> factory;

    public ImageConverterBuildItem(Supplier<ImageConverter> factory) {
        this.factory = factory;
    }

    public ImageConverter get() {
        return factory.get();
    }
}
