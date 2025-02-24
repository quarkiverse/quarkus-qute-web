package io.quarkiverse.qute.web.markdown.deployment;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigRoot
@ConfigMapping(prefix = "quarkus.qute.web.markdown")
public interface MarkdownConfig {

    /**
     * Configures tables plugin.
     */
    @WithName("plugin.tables")
    PluginConfig tables();

    /**
     * Configures autolink plugin.
     */
    @WithName("plugin.autolink")
    PluginConfig autolink();

    /**
     * Configures heading-anchor plugin.
     */
    @WithName("plugin.heading-anchor")
    PluginConfig headingAnchor();

    interface PluginConfig {

        /**
         * Whether the plugin is enabled or not.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
