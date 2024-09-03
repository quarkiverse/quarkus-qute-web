package io.quarkiverse.qute.web.markdown.autolink.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;

import io.quarkus.arc.DefaultBean;

@Dependent
public class AutolinkConfiguration {

    @Produces
    @DefaultBean
    public Extension autolink() {
        return AutolinkExtension.create();
    }
}
