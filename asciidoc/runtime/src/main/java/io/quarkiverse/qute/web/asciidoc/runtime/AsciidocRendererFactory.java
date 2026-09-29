package io.quarkiverse.qute.web.asciidoc.runtime;

import io.yupiik.asciidoc.renderer.Visitor;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

/**
 * Creates the renderer of each document, to render custom macros or to change the HTML: typically a subclass of
 * {@link AsciidoctorLikeHtmlRenderer} built with the given configuration, for example one overriding
 * {@code onMissingMacro} to render the macros of the site.
 * <p>
 * Implement it as a CDI bean, at most one. Without one, the converter uses
 * {@code new AsciidoctorLikeHtmlRenderer(configuration)}. A renderer is not thread-safe and keeps state between
 * documents, so return a new instance on each call.
 */
public interface AsciidocRendererFactory {

    Visitor<String> createRenderer(AsciidoctorLikeHtmlRenderer.Configuration configuration,
            AsciidocRenderingContext context);
}
