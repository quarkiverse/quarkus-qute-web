package io.quarkiverse.qute.web.image.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Declares a directory for image lookups. Extensions register these so the image processor
 * can discover and pre-process images from multiple sources.
 */
public final class ImagesDirBuildItem extends MultiBuildItem {

    private final String prefix;
    private final String urlPrefix;
    private final Path localPath;

    private ImagesDirBuildItem(String prefix, String urlPrefix, Path localPath) {
        this.prefix = normalizePrefix(prefix);
        this.urlPrefix = urlPrefix != null ? normalizePrefix(urlPrefix) : null;
        this.localPath = localPath;
    }

    /**
     * Classpath resource directory.
     *
     * @param prefix the classpath prefix (e.g. "web/static/images")
     */
    public static ImagesDirBuildItem of(String prefix) {
        return new ImagesDirBuildItem(prefix, null, null);
    }

    /**
     * Classpath resource directory with a URL prefix for indexing.
     * Files are scanned from {@code prefix} but indexed under {@code urlPrefix}.
     *
     * @param prefix the classpath prefix where files are located
     * @param urlPrefix the URL prefix for template lookups
     */
    public static ImagesDirBuildItem of(String prefix, String urlPrefix) {
        return new ImagesDirBuildItem(prefix, urlPrefix, null);
    }

    /**
     * Local filesystem directory with a URL prefix for indexing.
     * Files are scanned from {@code localPath} but indexed under {@code urlPrefix}.
     *
     * @param urlPrefix the URL prefix for template lookups (e.g. "images")
     * @param localPath the filesystem path to the directory
     */
    public static ImagesDirBuildItem of(String urlPrefix, Path localPath) {
        return new ImagesDirBuildItem(urlPrefix, urlPrefix, localPath);
    }

    public String prefix() {
        return prefix;
    }

    /**
     * The URL prefix used when indexing files for template lookup.
     * If null, files are indexed by their scopedPath (relative to the scope root).
     */
    public String urlPrefix() {
        return urlPrefix;
    }

    public Path localPath() {
        return localPath;
    }

    public boolean isLocal() {
        return localPath != null;
    }

    @Override
    public String toString() {
        if (localPath != null) {
            return "local:" + localPath + " (urlPrefix=" + urlPrefix + ")";
        }
        return "classpath:" + prefix + (urlPrefix != null ? " (urlPrefix=" + urlPrefix + ")" : "");
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        String normalized = prefix;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
