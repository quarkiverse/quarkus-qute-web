package io.quarkiverse.qute.web.asciidoc.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkus.arc.All;
import io.yupiik.asciidoc.model.Document;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.resolver.ContentResolver;
import io.yupiik.asciidoc.parser.resolver.RelativeContentResolver;
import io.yupiik.asciidoc.renderer.Visitor;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer.Configuration;

@Singleton
public class AsciidocConverter {

    private final Parser parser = new Parser();
    private static final ContentResolver EMPTY_CONTENT_RESOLVER = (ref, encoding) -> Optional.empty();
    // the renderer used when the application has no AsciidocRendererFactory bean
    private static final AsciidocRendererFactory DEFAULT_RENDERER_FACTORY = context -> new AsciidoctorLikeHtmlRenderer(
            new Configuration().setAttributes(context.attributes()));
    private final AsciidocConfig config;
    private final List<AsciidocDocumentProcessor> processors;
    private final AsciidocRendererFactory rendererFactory;

    @Inject
    public AsciidocConverter(AsciidocConfig config, @All List<AsciidocDocumentProcessor> processors,
            Instance<AsciidocRendererFactory> rendererFactory) {
        // an @All list is sorted by priority, highest first
        this(config, processors, rendererFactory.isUnsatisfied() ? DEFAULT_RENDERER_FACTORY : rendererFactory.get());
    }

    public AsciidocConverter(AsciidocConfig config) {
        this(config, List.of(), DEFAULT_RENDERER_FACTORY);
    }

    public AsciidocConverter(AsciidocConfig config, List<AsciidocDocumentProcessor> processors,
            AsciidocRendererFactory rendererFactory) {
        this.config = config;
        this.processors = List.copyOf(processors);
        this.rendererFactory = Objects.requireNonNullElse(rendererFactory, DEFAULT_RENDERER_FACTORY);
    }

    private Map<String, String> createAttributes(Map<String, String> asciidocAttributes,
            TemplateAttributes templateAttributes) {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("showtitle", "true");
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
        attributes.putAll(asciidocAttributes);
        return Collections.unmodifiableMap(attributes);
    }

    public String apply(String asciidoc, Map<String, String> asciidocAttributes, TemplateAttributes templateAttributes) {
        // Cleaning the content from global indentation is necessary because
        // AsciiDoc content is not supposed to be indented globally
        // In Qute context it might often be indented
        final String content = trimIndent(asciidoc);
        ContentResolver contentResolver = EMPTY_CONTENT_RESOLVER;
        if (templateAttributes.sourcePath() != null) {
            Path templateDir = Paths.get(templateAttributes.sourcePath()).getParent();
            contentResolver = new DiskAndClasspathResolver(templateDir.toString());
        }
        Document document = parser.parse(content, new Parser.ParserContext(contentResolver));
        final AsciidocRenderingContext context = new AsciidocRenderingContext(
                createAttributes(asciidocAttributes, templateAttributes), templateAttributes);
        for (AsciidocDocumentProcessor processor : processors) {
            document = processor.process(document, context);
        }
        // Renderer is not thread-safe and must not be shared
        final Visitor<String> renderer = rendererFactory.createRenderer(context);
        renderer.visit(document);
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

    public static class DiskAndClasspathResolver implements RelativeContentResolver {

        private final String baseDir;

        public DiskAndClasspathResolver(String baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public Optional<Resolved> resolve(Path parent, String ref, Charset encoding) {

            final var rel = Path.of(ref);
            if (rel.isAbsolute()) {
                return doRead(rel, encoding);
            }

            if (parent != null && parent.getParent() != null) {
                return doRead(parent.getParent().resolve(rel).normalize(), encoding);
            }

            final var baseDirPath = Path.of(baseDir);
            return doRead(baseDirPath.resolve(rel).normalize(), encoding);
        }

        private static Optional<Resolved> doRead(Path resolved, Charset encoding) {
            try (InputStream resource = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resolved.toString())) {
                if (resource != null) {
                    return Optional.of(new Resolved(resolved, new String(resource.readAllBytes(), encoding).lines().toList()));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't read '" + resolved + "'");
            }

            if (!Files.isRegularFile(resolved)) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Resolved(resolved, Files.readAllLines(resolved, encoding)));
            } catch (IOException e) {
                throw new IllegalStateException("Can't read '" + resolved + "'");
            }
        }
    }

}
