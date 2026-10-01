package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocDocumentProcessor;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocRendererFactory;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocRenderingContext;
import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;
import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.model.Document;
import io.yupiik.asciidoc.model.Element;
import io.yupiik.asciidoc.model.Macro;
import io.yupiik.asciidoc.model.Paragraph;
import io.yupiik.asciidoc.model.Text;
import io.yupiik.asciidoc.renderer.Visitor;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

/**
 * The two customization hooks: {@link AsciidocDocumentProcessor} beans rewrite the model before rendering, highest
 * priority first, and an {@link AsciidocRendererFactory} bean supplies the renderer, here one rendering a macro of
 * the site.
 */
public class QuteWebAsciidocHooksTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(app -> app
                    .addClasses(FirstProcessor.class, SecondProcessor.class, SiteRendererFactory.class)
                    .addAsResource(new StringAsset("{#ascii}A tooltip:term[its definition] here.{/ascii}"),
                            "templates/hooks.txt"));

    @Inject
    Engine engine;

    @Test
    void processorsRunHighestPriorityFirstAndTheFactorySuppliesTheRenderer() {
        final String html = engine.getTemplate("hooks").render();
        assertThat(html)
                // the site macro, rendered by the renderer the factory created, which read the rendering context
                .contains("<span class=\"tooltip\" title=\"its definition\" data-sitegen=\"roq\">term</span>")
                // the paragraphs the processors appended, priority 20 before priority 10
                .containsSubsequence("added by second", "added by first");
    }

    @Singleton
    @Priority(10)
    public static class FirstProcessor implements AsciidocDocumentProcessor {
        @Override
        public Document process(final Document document, final AsciidocRenderingContext context) {
            return append(document, "added by first");
        }
    }

    @Singleton
    @Priority(20)
    public static class SecondProcessor implements AsciidocDocumentProcessor {
        @Override
        public Document process(final Document document, final AsciidocRenderingContext context) {
            return append(document, "added by second");
        }
    }

    @Singleton
    public static class SiteRendererFactory implements AsciidocRendererFactory {
        @Override
        public Visitor<String> createRenderer(final AsciidocRenderingContext context) {
            final AsciidoctorLikeHtmlRenderer.Configuration configuration = new AsciidoctorLikeHtmlRenderer.Configuration()
                    .setAttributes(context.attributes());
            return new AsciidoctorLikeHtmlRenderer(configuration) {
                @Override
                protected void onMissingMacro(final Macro element) {
                    if ("tooltip".equals(element.name())) {
                        builder.append("<span class=\"tooltip\" title=\"").append(element.options().get(""))
                                .append("\" data-sitegen=\"").append(context.attributes().get("sitegen")).append("\">")
                                .append(element.label()).append("</span>");
                        return;
                    }
                    super.onMissingMacro(element);
                }
            };
        }
    }

    private static Document append(final Document document, final String text) {
        final List<Element> children = new ArrayList<>(document.body().children());
        children.add(new Paragraph(List.of(new Text(List.of(), text, Map.of())), Map.of()));
        return new Document(document.header(), new Body(children));
    }
}
