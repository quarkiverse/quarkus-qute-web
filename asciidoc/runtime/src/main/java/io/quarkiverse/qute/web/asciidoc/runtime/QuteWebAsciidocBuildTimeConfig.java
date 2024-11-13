package io.quarkiverse.qute.web.asciidoc.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.web.asciidoc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface QuteWebAsciidocBuildTimeConfig {

    /**
     * By default, diagram block are left as is and rendered as code block in the final rendering.
     *
     * If set to `true`, they will be prerendered using a kroki server and rendered as image.
     *
     * [WARNING]
     * ----
     * If set to `true`, the `quarkus.rest-client.kroki.url` property will be mandatory at runtime.
     * ----
     *
     * @asciidoclet
     */
    @WithDefault("false")
    boolean prerenderDiagram();
}
