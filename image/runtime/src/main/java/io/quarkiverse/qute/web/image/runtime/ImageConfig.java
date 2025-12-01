package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.qute.image")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface ImageConfig {

    /**
     * Global CSS media queries, referenced by name (e.g., mobile, tablet, desktop).
     * Example: desktop -> "max-width: 1200px"
     */
    Map<String, String> mediaQueries();

    /**
     * Preset definitions.
     */
    Map<String, PresetConfig> presets();

}
