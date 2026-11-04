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
