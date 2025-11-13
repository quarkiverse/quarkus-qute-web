package io.quarkiverse.qute.web.image.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Specifies directories allowed for resolving relative paths in the local filesystem.
 * Resources are always allowed.
 * <p>
 * Any relative path must reside within one of these directories; otherwise,
 * resolution fails.
 * </p>
 */
public final class WhitelistDirBuildItem extends MultiBuildItem {

    private final Path path;

    public WhitelistDirBuildItem(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }
}
