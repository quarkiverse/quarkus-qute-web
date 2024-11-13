package io.quarkiverse.qute.web.asciidoc.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.web.asciidoctor")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface AsciidoctorConfig {

    /**
     * Where images will be rendered
     */
    @WithDefault("target/images/")
    String outputImageDir();

    /**
     * Where images will linked to.oq
     *
     */
    @WithDefault("/public")
    String imageDir();
}