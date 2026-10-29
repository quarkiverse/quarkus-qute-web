package io.quarkiverse.qute.web.asciidoc.runtime;

import io.yupiik.asciidoc.model.Document;

/**
 * Rewrites the parsed {@link Document} before it is rendered, as an Asciidoctor tree processor does.
 * <p>
 * Implement it as a CDI bean. The converter applies every bean, highest {@code @Priority} first, each one receiving
 * the document the previous one returned. The model is immutable, so return a new document, or the same one to change
 * nothing.
 */
public interface AsciidocDocumentProcessor {

    Document process(Document document, AsciidocRenderingContext context);
}
