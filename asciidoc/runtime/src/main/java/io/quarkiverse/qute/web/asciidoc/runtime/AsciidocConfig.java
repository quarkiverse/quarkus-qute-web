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
     * <p>
     * Note that <code>mermaid-skipModule</code> attribute presence will not inject mermaid.js script
     * so you will have to ensure it is present in your layout/scripts by default.
     * If not set it will be injected using a <pre>cdn.jsdelivr.net</pre> url and version 11.
     */
    Map<String, String> attributes();

}
