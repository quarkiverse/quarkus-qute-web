package io.quarkiverse.qute.web.image.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class ImageBuildItem extends SimpleBuildItem {
    public final Path path;
    public final String image;

    public ImageBuildItem(String image, Path path) {
        this.image = image;
        this.path = path;
    }
}
