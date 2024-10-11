package io.quarkiverse.qute.web.markdown.runtime.commonmark;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.commonmark.Extension;

import io.quarkiverse.qute.web.markdown.runtime.MdConverter;
import io.quarkus.arc.DefaultBean;

@Dependent
public class MdConfiguration {

    @Inject
    Instance<Extension> extensions;

    @DefaultBean
    @Produces
    public MdConverter mdConverter() {
        return new CommonMarkConverter(extensions.stream().toList());
    }

}
