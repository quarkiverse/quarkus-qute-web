package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.asciidoc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface AsciidocConfig {

    /**
     * Defines the Asciidoc attributes to be applied during rendering.
     * <p>
     * Default values:
     * <ul>
     * <li><code>noheader=true</code></li>
     * </ul>
     */
    Map<String, String> attributes();

}
