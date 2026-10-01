package io.quarkiverse.qute.web.asciidoc.runtime;

import io.yupiik.asciidoc.renderer.Visitor;

/**
 * Creates the renderer of each document, to render custom macros or to change the HTML: typically a subclass of
 * {@link io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer} configured with
 * {@link AsciidocRenderingContext#attributes()}, for example one overriding {@code onMissingMacro} to render the
 * macros of the site.
 * <p>
 * Implement it as a CDI bean, at most one. Without one, the converter uses an {@code AsciidoctorLikeHtmlRenderer}
 * configured with the same attributes. A renderer is not thread-safe and keeps state between documents, so return a
 * new instance on each call.
 */
public interface AsciidocRendererFactory {

    Visitor<String> createRenderer(AsciidocRenderingContext context);
}
