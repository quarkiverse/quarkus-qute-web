package io.quarkiverse.qute.web.image.spi.items;

import java.nio.file.Path;
import java.util.function.Function;

import io.quarkus.builder.item.MultiBuildItem;

/**
 *
 * <p>
 * Represents an additional resource folder that extensions can register for image lookups
 * using absolute paths.
 * </p>
 * <p>
 * Files in this folder are expected to be served through the provided public path rewriter.
 * When null, it is considered as not served.
 * A custom rewrite function can be defined to map source-declared paths to their public
 * counterparts.
 * </p>
 *
 * <pre>
 * Example (basePath is <code>/</code>):
 *   Source path: "/Foé/some.jpg"
 *   Public path: "/fo-/some.jpg"
 * </pre>
 */
public final class ImageSourceDirBuildItem extends MultiBuildItem {

    private final Path basePath;
    private final boolean isResource;
    private final Function<String, String> toPublicPathFunction;

    public ImageSourceDirBuildItem(Path basePath, boolean isResource, Function<String, String> toPublicPathFunction) {
        this.basePath = basePath;
        this.isResource = isResource;
        this.toPublicPathFunction = toPublicPathFunction;
    }

    public boolean isResource() {
        return isResource;
    }

    public Path basePath() {
        return basePath;
    }

    public boolean isServed() {
        return toPublicPathFunction != null;
    }

    public String toPublicPath(String path) {
        if (toPublicPathFunction == null) {
            return path;
        }
        return toPublicPathFunction.apply(path);
    }
}
