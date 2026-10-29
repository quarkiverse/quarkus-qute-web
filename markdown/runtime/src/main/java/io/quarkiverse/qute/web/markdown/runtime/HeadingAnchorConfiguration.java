package io.quarkiverse.qute.web.markdown.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.commonmark.Extension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;

@Dependent
public class HeadingAnchorConfiguration {

    @Produces
    public Extension headingAnchor() {
        return HeadingAnchorExtension.create();
    }
}
