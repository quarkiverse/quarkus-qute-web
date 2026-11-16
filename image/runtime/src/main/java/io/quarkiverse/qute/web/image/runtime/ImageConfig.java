package io.quarkiverse.qute.web.image.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Log each download and each converted source at INFO during the build, with sizes and times. Off by default:
     * the build logs one line when it starts and a summary.
     */
    @WithDefault("false")
    boolean verbose();

    /**
     * When true, an image source that was not processed at build time is a rendering error instead of
     * falling back to a plain {@code <img>} pointing at the source, and a remote source that cannot be
     * downloaded fails the build instead of rendering as is. A null or blank source renders nothing in both modes.
     */
    @WithDefault("false")
    boolean strict();

    /**
     * Remote sources: {@code http(s)} urls are downloaded at build time, cached under the build dir, and
     * converted like local files.
     */
    Remote remote();

    interface Remote {
        /**
         * Download and convert remote sources. When false, a remote source renders as is.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * The timeout of one download.
         */
        @WithDefault("20s")
        Duration timeout();

        /**
         * Download plain {@code http://} sources too. Off by default: without TLS anyone on the path can replace
         * the image, and the bytes are parsed at build time.
         */
        @WithDefault("false")
        boolean allowHttp();
    }

    /**
     * The public path under which generated images are served and, for static output, written.
     * Must start with a slash and not end with one.
     */
    @WithDefault("/static/images/generated")
    String outputPath();

    /**
     * The directory where generated images are written at build time, and remote originals cached. Relative paths
     * are resolved against the build output directory ({@code target/} or {@code build/}): use {@code ../.qute-image}
     * to keep it in the project, surviving a clean build, under version control or not. The directory is kept
     * between builds: a variant whose file already exists is reused, and its name changes whenever the source
     * bytes, width, format, crop, quality or converter change.
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
     * The presets, by name. The {@code default} preset exists whether configured or not, see {@link #preset(String)}.
     */
    Map<String, PresetConfig> presets();

    /**
     * The preset with the given name. {@code default} is the built-in default preset unless it is configured;
     * any other name must be configured.
     *
     * @return the preset, or empty when the name is not a preset
     */
    default Optional<PresetConfig> preset(String name) {
        PresetConfig configured = presets().get(name);
        if (configured != null) {
            return Optional.of(configured);
        }
        return "default".equals(name) ? Optional.of(PresetConfig.DEFAULT) : Optional.empty();
    }

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
