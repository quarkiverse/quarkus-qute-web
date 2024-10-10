package io.quarkiverse.qute.web.markdown.runtime;

import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.qute.web.markdown.runtime.commonmark.CommonMarkConverter;
import io.quarkus.qute.TemplateExtension;

@Vetoed
@TemplateExtension
public class QuteWebMarkdownExtensions {

    private static final CommonMarkConverter converter = new CommonMarkConverter(List.of());

    static String markdownify(String text) {
        return converter.apply(text);
    }
}
