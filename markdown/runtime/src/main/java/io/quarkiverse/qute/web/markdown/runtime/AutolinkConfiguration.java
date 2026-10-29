package io.quarkiverse.qute.web.markdown.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;

@Dependent
public class AutolinkConfiguration {

    @Produces
    public Extension autolink() {
        return AutolinkExtension.create();
    }
}
