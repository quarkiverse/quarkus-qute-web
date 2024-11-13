package io.quarkiverse.qute.web.asciidoc.runtime.kroki;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.web.asciidoc.kroki")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface KrokiConfig {

    /**
     * Prerender diagram with kroki
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Prerender diagram with kroki
     */
    @WithDefault("target/images/")
    String outputDirBasePath();

    /**
     * Prerender diagram with kroki
     */
    @WithDefault("/images/")
    String imageBaseUrl();

}
