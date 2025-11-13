package io.quarkiverse.qute.web.image.deployment.items;

import io.quarkiverse.qute.web.image.runtime.Images;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item to pass the Images instance from build-time templates to run-time template analysis.
 */
public final class ImagesBuildItem extends SimpleBuildItem {
    public final Images images;

    public ImagesBuildItem(Images images) {
        this.images = images;
    }
}
