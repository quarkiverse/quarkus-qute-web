package io.quarkiverse.qute.web.image.dynamic.runtime;

/**
 * Runtime-serializable representation of an image source directory.
 *
 * @param prefix the classpath prefix or scoped path prefix
 * @param localPath filesystem path for local directories, null for classpath-only
 */
public record ImageDir(String prefix, String localPath) {
}
