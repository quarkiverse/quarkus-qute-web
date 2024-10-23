package io.quarkiverse.qute.web.markdown.heading.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.commonmark.Extension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;

@Dependent
public class HeadingAnchorConfiguration {

    @Produces
    @Named("headingAnchor")
    public Extension autolink() {
        return HeadingAnchorExtension.create();
    }
}
