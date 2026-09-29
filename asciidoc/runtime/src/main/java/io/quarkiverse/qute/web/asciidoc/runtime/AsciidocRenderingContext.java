package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.Map;

/**
 * What the converter knows about the AsciiDoc content being rendered, given to an
 * {@link AsciidocDocumentProcessor} and an {@link AsciidocRendererFactory}.
 * <p>
 * An interface rather than a record, so it can gain methods without breaking implementations.
 */
public interface AsciidocRenderingContext {

    /**
     * @return the attributes the renderer is configured with: the extension configuration, the ones passed to the
     *         section, and the site and page ones the converter adds.
     */
    Map<String, String> attributes();

    /**
     * @return the template and site information of the content; {@code sourcePath()} is {@code null} for content
     *         without a file.
     */
    AsciidocConverter.TemplateAttributes templateAttributes();
}
