package io.quarkiverse.qute.web.image.deployment.items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * An image another extension wants converted at build time with a given preset, registered under a declared
 * path so that a template whose {@code src} evaluates to that path at render time finds the generated variants.
 * <p>
 * The declared path is the string the template expression produces at render time (for instance the
 * root-prefixed, URI encoded URL of a page attachment). Tags registered this way are not bound to a template
 * (wildcard template id), so any template can use them.
 */
public final class QuteImageToProcessBuildItem extends MultiBuildItem {

    private final String declaredPath;
    private final String presetName;
    private final Path file;
    private final String fileName;
    private byte[] contents;

    private QuteImageToProcessBuildItem(String declaredPath, String presetName, Path file, String fileName,
            byte[] contents) {
        this.declaredPath = Objects.requireNonNull(declaredPath, "declaredPath");
        this.presetName = presetName == null || presetName.isBlank() ? "default" : presetName;
        this.file = file;
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.contents = contents;
    }

    /**
     * An image stored on the local file system; its bytes are read when first needed.
     */
    public static QuteImageToProcessBuildItem of(String declaredPath, String presetName, Path file) {
        Objects.requireNonNull(file, "file");
        return new QuteImageToProcessBuildItem(declaredPath, presetName, file, file.getFileName().toString(), null);
    }

    /**
     * An image whose bytes are already in memory (classpath resource, generated content).
     *
     * @param fileName a file name with an extension, used for the generated file names and the original format
     */
    public static QuteImageToProcessBuildItem of(String declaredPath, String presetName, String fileName,
            byte[] contents) {
        return new QuteImageToProcessBuildItem(declaredPath, presetName, null, fileName,
                Objects.requireNonNull(contents, "contents"));
    }

    public String declaredPath() {
        return declaredPath;
    }

    public String presetName() {
        return presetName;
    }

    /**
     * @return the local file, or null when the image was given as bytes
     */
    public Path file() {
        return file;
    }

    public String fileName() {
        return fileName;
    }

    public byte[] contents() {
        if (contents == null) {
            try {
                contents = Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read image to process: " + file, e);
            }
        }
        return contents;
    }

    @Override
    public String toString() {
        return "QuteImageToProcessBuildItem{" + declaredPath + " (" + presetName + ") <- "
                + (file != null ? file : fileName) + "}";
    }
}
