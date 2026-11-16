package io.quarkiverse.qute.web.image.deployment.items;

import io.quarkiverse.qute.web.image.deployment.items.model.ImageStats;
import io.quarkiverse.qute.web.image.deployment.items.model.ImagesBuilder;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The accumulator of the processing; other extensions consume {@link QuteImageProcessedBuildItem} instead.
 */
public final class ImagesBuildItem extends SimpleBuildItem {
    private final ImagesBuilder builder = new ImagesBuilder();
    private final ImageStats stats = new ImageStats();
    private final String urlPrefix;

    /**
     * @param urlPrefix the HTTP root path without trailing slash, empty at the root
     */
    public ImagesBuildItem(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public ImagesBuilder builder() {
        return builder;
    }

    public String urlPrefix() {
        return urlPrefix;
    }

    public ImageStats stats() {
        return stats;
    }

}
