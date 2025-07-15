package io.quarkiverse.qute.web.asciidoc.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.resolver.ContentResolver;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer.Configuration;

@Singleton
public class AsciidocConverter {

    private final Parser parser = new Parser();
    private static final ContentResolver EMPTY_CONTENT_RESOLVER = (ref, encoding) -> Optional.empty();
    private final AsciidocConfig config;

    @Inject
    public AsciidocConverter(AsciidocConfig config) {
        this.config = config;
    }

    private Configuration createConfiguration(TemplateAttributes templateAttributes) {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("sitegen", "roq");
        attributes.put("relfileprefix", "../");
        attributes.put("relfilesuffix", "/");
        attributes.put("noheader", "true");
        if (templateAttributes.pageUrl() != null) {
            attributes.put("page-url", templateAttributes.pageUrl());
        }
        if (templateAttributes.pagePath() != null) {
            attributes.put("page-path", templateAttributes.pagePath());
        }
        if (templateAttributes.siteUrl() != null) {
            attributes.put("site-url", templateAttributes.siteUrl());
        }
        if (templateAttributes.sitePath() != null) {
            attributes.put("site-path", templateAttributes.sitePath());
        }
        attributes.putAll(config.attributes());
        return new Configuration()
                .setAttributes(attributes);
    }

    public String apply(String asciidoc, TemplateAttributes templateAttributes) {
        // Cleaning the content from global indentation is necessary because
        // AsciiDoc content is not supposed to be indented globally
        // In Qute context it might often be indented
        final String content = trimIndent(asciidoc);
        ContentResolver contentResolver = EMPTY_CONTENT_RESOLVER;
        if (templateAttributes.sourcePath() != null) {
            Path templateDir = Paths.get(templateAttributes.sourcePath()).getParent();
            if (Files.isDirectory(templateDir)) {
                contentResolver = ContentResolver.of(templateDir);
            }
        }
        Body body = parser.parseBody(content, new Parser.ParserContext(contentResolver));
        // Renderer is not thread-safe and must not be shared
        final Configuration configuration = createConfiguration(templateAttributes);
        AsciidoctorLikeHtmlRenderer renderer = new AsciidoctorLikeHtmlRenderer(configuration);
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

    public record TemplateAttributes(String sourcePath,
            String siteUrl,
            String sitePath,
            String pageUrl,
            String pagePath) {

    }

}
