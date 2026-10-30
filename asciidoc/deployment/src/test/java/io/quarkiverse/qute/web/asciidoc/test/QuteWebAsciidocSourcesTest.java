package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocDocumentProcessor;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocRenderingContext;
import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;
import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.model.Document;
import io.yupiik.asciidoc.model.Element;
import io.yupiik.asciidoc.model.Paragraph;
import io.yupiik.asciidoc.model.Section;
import io.yupiik.asciidoc.model.Text;
import io.yupiik.asciidoc.parser.SourceSpan;

/**
 * A processor reads where each block-level element came from, for example to look up the translation of its source.
 */
public class QuteWebAsciidocSourcesTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(app -> app
                    .addClasses(SourcesProcessor.class)
                    .addAsResource(new StringAsset("""
                            {#ascii}
                            First paragraph
                            on two lines.

                            Second paragraph.

                            == Section

                            Nested paragraph.
                            {/ascii}"""), "templates/sources.txt"));

    @Inject
    Engine engine;

    @Test
    void processorsSeeTheSourceOfEachElement() {
        assertThat(engine.getTemplate("sources").render())
                .contains("sources: [1-2] First paragraph/on two lines. | [4-4] Second paragraph."
                        + " | [6-8] == Section//Nested paragraph. { [8-8] Nested paragraph. }");
    }

    @Singleton
    public static class SourcesProcessor implements AsciidocDocumentProcessor {
        @Override
        public Document process(final Document document, final AsciidocRenderingContext context) {
            final String summary = document.body().children().stream()
                    .map(element -> describe(element, context))
                    .collect(Collectors.joining(" | ", "sources: ", ""));
            final List<Element> children = new ArrayList<>(document.body().children());
            children.add(new Paragraph(List.of(new Text(List.of(), summary, Map.of())), Map.of()));
            return new Document(document.header(), new Body(children));
        }

        private static String describe(final Element element, final AsciidocRenderingContext context) {
            final SourceSpan span = context.sources().get(element);
            final String described = span == null
                    ? "none"
                    : "[" + span.startLine() + "-" + span.endLine() + "] " + span.source().replace('\n', '/');
            if (element instanceof Section section) {
                // the elements of a section have their own spans
                return described + " { " + section.children().stream()
                        .map(child -> describe(child, context))
                        .collect(Collectors.joining(" | ")) + " }";
            }
            return described;
        }
    }
}
