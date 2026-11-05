package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.Map;

import io.yupiik.asciidoc.model.Element;
import io.yupiik.asciidoc.parser.SourceSpan;

/**
 * What the converter knows about the AsciiDoc content being rendered, given to each
 * {@link AsciidocDocumentProcessor} and to the {@link AsciidocRendererFactory}.
 *
 * @param attributes the attributes of the document, which the renderer is configured with: the extension
 *        configuration, the ones passed to the section, and the site and page ones the converter adds
 * @param templateAttributes the template and site information of the content; {@code sourcePath()} is {@code null}
 *        for content without a file
 * @param sources where each block-level element of the document body was read from, keyed by identity, because
 *        elements are records that compare by value; the lines are those of the content as Qute rendered it, with
 *        its common indentation removed, so expressions are already replaced; line numbers count from the start of
 *        that content, or of the list item or table cell, with a {@code null} file, and only the elements of an
 *        included file yielding several blocks have its path and line numbers; the elements the parser rebuilds or
 *        creates after reporting (the document title, the text of a list item, the content of an admonition, the
 *        texts of a description list, header cells, callout texts) and an element a processor creates have no entry
 */
public record AsciidocRenderingContext(Map<String, String> attributes,
        AsciidocConverter.TemplateAttributes templateAttributes,
        Map<Element, SourceSpan> sources) {
}
