package io.quarkiverse.qute.web.markdown.tables.runtime;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;

import io.quarkus.arc.DefaultBean;

@Dependent
public class TablesConfiguration {

    @Produces
    @DefaultBean
    public Extension tables() {
        return TablesExtension.create();
    }
}
