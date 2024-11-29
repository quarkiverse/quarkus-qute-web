package io.quarkiverse.qute.web.asciidoc.runtime;

import jakarta.enterprise.context.ApplicationScoped;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class AsciidoctorConverter {

    private final Asciidoctor asciidoctor;
    private final Options converterOptions;

    public AsciidoctorConverter(AsciidoctorConfig asciidoctorConfig) {
        asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.requireLibrary("asciidoctor-diagram");
        converterOptions = Options.builder()
                .backend("html5")
                .attributes(Attributes.builder()
                        .imagesDir(asciidoctorConfig.imageDir())
                        .showTitle(true)
                        .attribute("imagesoutdir", asciidoctorConfig.outputImageDir())
                        .attribute(Attributes.SOURCE_HIGHLIGHTER, "highlight.js")
                        .build())
                .build();
    }

    public String apply(String asciidoc) {
        // Cleaning the content from global indentation is necessary because
        // AsciiDoc content is not supposed to be indented globally
        // In Qute context it might often be indented
        final String content = trimIndent(asciidoc);
        return asciidoctor.convert(content, converterOptions);
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
