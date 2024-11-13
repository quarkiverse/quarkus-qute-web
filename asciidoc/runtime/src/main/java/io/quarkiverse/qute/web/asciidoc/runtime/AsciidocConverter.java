package io.quarkiverse.qute.web.asciidoc.runtime;

import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;
import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.resolver.ContentResolver;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

@ApplicationScoped
@Unremovable
public class AsciidocConverter {

    private final Parser parser = new Parser();
    private final AsciidocRendererFactory rendererFactory;

    public AsciidocConverter(AsciidocRendererFactory rendererFactory) {
        this.rendererFactory = rendererFactory;
    }

    public String apply(String asciidoc) {
        // Cleaning the content from global indentation is necessary because
        // AsciiDoc content is not supposed to be indented globally
        // In Qute context it might often be indented
        final String content = trimIndent(asciidoc);
        Body body = parser.parseBody(content, new Parser.ParserContext(ContentResolver.of(Path.of("."))));
        // Renderer is not thread-safe and must not be shared
        AsciidoctorLikeHtmlRenderer renderer = rendererFactory.getRenderer();
        renderer.visitBody(body);
        return renderer.result();
    }

    public static String trimIndent(String content) {
        int minIndent = Integer.MAX_VALUE;
        boolean foundNonEmptyLine = false;

        // Calculate minimum indentation in a single pass
        final String[] lines = content.split("\\v");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int leadingSpaces = line.indexOf(line.trim());
                minIndent = Math.min(minIndent, leadingSpaces);
                foundNonEmptyLine = true;
            }
        }

        // If no indentation needs removal, or all lines are empty, return original content
        if (!foundNonEmptyLine || minIndent == 0) {
            return content;
        }

        // Build the output with trimmed indent
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (line.length() >= minIndent) {
                result.append(line.substring(minIndent));
            } else {
                result.append(line); // Preserve empty lines as-is
            }
            result.append("\n");
        }

        return result.toString();
    }

}
