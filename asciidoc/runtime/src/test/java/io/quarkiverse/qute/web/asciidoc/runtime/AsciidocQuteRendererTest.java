package io.quarkiverse.qute.web.asciidoc.runtime;

import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsciidocQuteRendererTest {
    @Test
    void mermaid() {
        assertRendered(
                """
                        [mermaid]
                        ....
                        sequenceDiagram
                            participant Alice
                            participant Bob
                            Alice->>Bob: Hello Bob, how are you?
                            Bob-->>Alice: Great!
                        ....
                        """,
                """
                        <pre class="mermaid">
                        sequenceDiagram
                            participant Alice
                            participant Bob
                            Alice->>Bob: Hello Bob, how are you?
                            Bob-->>Alice: Great!
                        </pre>
                        <script type="module">import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';</script>
                        """
        );
    }

    private void assertRendered(final String adoc, final String html) {
        final var parser = new Parser();
        final var ast = parser.parseBody(adoc, new Parser.ParserContext((ref, encoding) -> Optional.empty()));
        final var renderer = new AsciidocQuteRenderer(new AsciidoctorLikeHtmlRenderer.Configuration());
        renderer.visitBody(ast);
        assertEquals(html, renderer.result());
    }
}
