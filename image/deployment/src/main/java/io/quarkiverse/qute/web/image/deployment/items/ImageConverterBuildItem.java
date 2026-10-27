package io.quarkiverse.qute.web.image.deployment.items;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ImageConverterBuildItem extends SimpleBuildItem {

    private final ImageConverter converter;

    public ImageConverterBuildItem(ImageConverter converter) {
        this.converter = converter;
    }

    public ImageConverter get() {
        return converter;
    }
}
