package io.quarkiverse.qute.web.markdown.heading.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.commonmark.Extension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;

import io.quarkus.arc.DefaultBean;

@Dependent
public class HeadingAnchorConfiguration {

    @Produces
    @DefaultBean
    public Extension headingAnchor() {
        return HeadingAnchorExtension.create();
    }
}
