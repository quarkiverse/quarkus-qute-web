package io.quarkiverse.qute.web.markdown.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;

import io.quarkus.arc.DefaultBean;

@Dependent
public class AlertsConfiguration {

    @Inject
    AlertsConfig config;

    @Produces
    @DefaultBean
    public Extension alerts() {
        var builder = AlertsExtension.builder();
        config.customTypes().forEach(builder::addCustomType);
        return builder.build();
    }
}
