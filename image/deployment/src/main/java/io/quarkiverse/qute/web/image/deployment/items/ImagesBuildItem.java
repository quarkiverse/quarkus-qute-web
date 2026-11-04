package io.quarkiverse.qute.web.image.deployment.items;

import io.quarkiverse.qute.web.image.deployment.items.model.ImagesBuilder;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ImagesBuildItem extends SimpleBuildItem {
    private final ImagesBuilder builder = new ImagesBuilder();

    public ImagesBuildItem() {
    }

    public ImagesBuilder builder() {
        return builder;
    }

}
