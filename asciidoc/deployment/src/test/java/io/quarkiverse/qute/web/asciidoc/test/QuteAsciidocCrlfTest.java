package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Indented AsciiDoc content with Windows line endings, as in a template checked out on Windows, keeps its paragraphs.
 */
public class QuteAsciidocCrlfTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(app -> app
                    .addAsResource(new StringAsset("{#ascii}\r\n    First line\r\n    of one paragraph.\r\n\r\n"
                            + "    Second paragraph.\r\n{/ascii}"), "templates/crlf.txt"));

    @Inject
    Engine engine;

    @Test
    void indentedContentWithWindowsLineEndingsKeepsItsParagraphs() {
        final String html = engine.getTemplate("crlf").render();
        assertThat(html)
                .containsIgnoringWhitespaces("First line of one paragraph.")
                .containsIgnoringWhitespaces("Second paragraph.");
        assertThat(html.split("class=\"paragraph\"", -1)).as(html).hasSize(3);
    }
}
