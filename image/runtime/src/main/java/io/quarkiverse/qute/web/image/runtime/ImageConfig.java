package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.image")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface ImageConfig {

    /**
     * When true, slugify the base filename in generated output paths
     * (strip accents, lowercase, replace non-alphanumeric with dashes).
     * Produces clean URLs for images with special characters.
     */
    @WithDefault("true")
    boolean slugifyOutput();

    /**
     * When true, an image source that was not processed at build time is a rendering error instead of
     * falling back to a plain {@code <img>} pointing at the source. Remote http(s) URLs are never looked up
     * and always render as is; a null or blank source renders nothing in both modes.
     */
    @WithDefault("false")
    boolean strict();

    /**
     * The public path under which generated images are served and, for static output, written.
     * Must start with a slash and not end with one.
     */
    @WithDefault("/static/images/generated")
    String outputPath();

    /**
     * The directory where generated images are written at build time. Relative paths are resolved against the
     * build output directory (for instance {@code target/}). The directory is kept between builds: a variant whose
     * file already exists is reused, and its name changes whenever the source bytes, width, format, crop, quality
     * or converter change.
     */
    @WithDefault("qute-image")
    String buildDir();

    /**
     * Source file extensions that are never converted: the image is used as is, without generated variants
     * (no srcset, no picture, no width or height). Vectors are listed by default since rasterizing them makes
     * them larger and blurrier. Case-insensitive. To let a converter rasterize SVGs, set a value that matches no
     * extension, for instance {@code none} (an empty value falls back to the default).
     */
    @WithDefault("svg")
    List<String> passthrough();

    /**
     * Preset definitions.
     * If empty, the default configuration is used.
     */
    Map<String, PresetConfig> presets();

    /**
     * Glob patterns for bulk image pre-processing at build time.
     * Matched images are processed with the specified presets even if not
     * directly referenced in a template.
     */
    List<GeneratePattern> generate();

    interface GeneratePattern {
        /**
         * Glob pattern to match image files (e.g., "**&#47;*.jpg", "content/posts/**").
         * Matched against the scoped path within image directories.
         */
        String glob();

        /**
         * Preset names to apply to matched images.
         * References entries from the presets map.
         */
        List<String> presets();
    }
}
