package io.quarkiverse.qute.web.markdown.test;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.commonmark.Extension;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A CommonMark {@link Extension} bean is added to the parser and the renderer next to the built-in extensions, here one
 * customizing the HTML rendering of paragraphs.
 */
public class QuteWebMarkdownExtensionBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(app -> app
                    .addClasses(LeadParagraphExtension.class)
                    .addAsResource(new StringAsset("{#markdown}# Qute\n\nHello *Qute*{/markdown}"),
                            "templates/extension.txt"));

    @Inject
    Engine engine;

    @Test
    void extensionBeanCustomizesTheRendering() {
        // the heading anchor of the built-in extension is kept
        Assertions.assertEquals("<h1 id=\"qute\">Qute</h1>\n<p class=\"lead\">Hello <em>Qute</em></p>\n",
                engine.getTemplate("extension").render());
    }

    @Singleton
    public static class LeadParagraphExtension implements Extension, HtmlRenderer.HtmlRendererExtension {
        @Override
        public void extend(HtmlRenderer.Builder rendererBuilder) {
            rendererBuilder.attributeProviderFactory(context -> new LeadParagraphAttributeProvider());
        }
    }

    public static class LeadParagraphAttributeProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Paragraph) {
                attributes.put("class", "lead");
            }
        }
    }
}
