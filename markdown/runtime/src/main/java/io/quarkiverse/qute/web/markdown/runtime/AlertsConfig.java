package io.quarkiverse.qute.web.markdown.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.web.markdown.plugin.alerts")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface AlertsConfig {

    /**
     * Custom alert types beyond the standard GFM types (NOTE, TIP, IMPORTANT, WARNING, CAUTION).
     * <p>
     * Key is the alert type (must be uppercase), value is the display title.
     * <p>
     * Example configuration:
     *
     * <pre>
     * quarkus.qute.web.markdown.plugin.alerts.custom-types.INFO=Information
     * quarkus.qute.web.markdown.plugin.alerts.custom-types.BUG=Known Bug
     * </pre>
     *
     * @return map of custom alert types
     */
    @WithDefault("{}")
    Map<String, String> customTypes();
}
