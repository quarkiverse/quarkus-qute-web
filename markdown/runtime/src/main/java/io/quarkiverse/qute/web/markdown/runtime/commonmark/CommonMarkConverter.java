package io.quarkiverse.qute.web.markdown.runtime.commonmark;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class CommonMarkConverter {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public String apply(String markdown) {
        Node node = parser.parse(markdown);
        return htmlRenderer.render(node);
    }
}
