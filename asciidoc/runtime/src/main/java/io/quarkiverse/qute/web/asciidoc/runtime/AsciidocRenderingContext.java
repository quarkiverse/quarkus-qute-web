package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.Map;

/**
 * What the converter knows about the AsciiDoc content being rendered, given to each
 * {@link AsciidocDocumentProcessor} and to the {@link AsciidocRendererFactory}.
 *
 * @param attributes the attributes of the document, which the renderer is configured with: the extension
 *        configuration, the ones passed to the section, and the site and page ones the converter adds
 * @param templateAttributes the template and site information of the content; {@code sourcePath()} is {@code null}
 *        for content without a file
 */
public record AsciidocRenderingContext(Map<String, String> attributes,
        AsciidocConverter.TemplateAttributes templateAttributes) {
}
