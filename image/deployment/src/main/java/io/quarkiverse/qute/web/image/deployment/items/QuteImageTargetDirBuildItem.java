package io.quarkiverse.qute.web.image.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class QuteImageTargetDirBuildItem extends SimpleBuildItem {
    public final Path path;

    public QuteImageTargetDirBuildItem(Path path) {
        this.path = path;
    }
}
