package io.quarkiverse.qute.web.asciidoc.deployment.devservice;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.web.asciidoc.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface PrerenderingDevservicesConfig {

    /**
     * Enable or disable Dev Services explicitly. Dev Services are automatically enabled unless {@code quarkus.minio.url} is
     * set.
     */
    @WithDefault("true")
    Boolean enabled();

    /**
     * Optional fixed port the dev service will listen to.
     * <p>
     * If not defined, the port will be chosen randomly.
     */
    @WithDefault("0")
    Integer port();

    /**
     * The Minio container image to use.
     */
    @WithDefault("yuzutech/kroki:0.26.0")
    String imageName();
}
