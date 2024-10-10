package io.quarkiverse.qute.web.markdown.runtime;

import jakarta.enterprise.inject.Vetoed;

import org.commonmark.Extension;

import io.quarkiverse.qute.web.markdown.runtime.commonmark.CommonMarkConverter;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.qute.TemplateExtension;

@Vetoed
@TemplateExtension
public class QuteWebMarkdownExtensions {

    private static final CommonMarkConverter converter = new CommonMarkConverter(
            Arc.container().listAll(Extension.class).stream().map(InstanceHandle::get).toList());

    static String markdownify(String text) {
        String apply = converter.apply(text);
        return apply;
    }
}
