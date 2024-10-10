package io.quarkiverse.qute.web.asciidoc.runtime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateExtension;

@Vetoed
@TemplateExtension
public class AsciidocExtensions {

    private static final AsciidocConverter CONVERTER = new AsciidocConverter();

    static String asciidocify(String text) {
        return CONVERTER.apply(text);
    }
}
