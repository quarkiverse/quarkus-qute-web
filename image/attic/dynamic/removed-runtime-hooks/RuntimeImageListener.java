package io.quarkiverse.qute.web.image.runtime;

import java.nio.file.Path;

/**
 * Listener for images resolved and converted at runtime (not pre-processed at build time).
 * <p>
 * Implement this interface as a CDI bean to be notified when a dynamic image variant
 * has been converted (e.g. to copy it into a static site output).
 */
public interface RuntimeImageListener {

    /**
     * Called after a runtime image variant has been converted and written to disk.
     *
     * @param outputPath the web-relative output path (e.g. "/static/images/generated/abc/photo-640-xyz.webp")
     * @param convertedFile the filesystem path to the converted file
     */
    void onImageConverted(String outputPath, Path convertedFile);
}
