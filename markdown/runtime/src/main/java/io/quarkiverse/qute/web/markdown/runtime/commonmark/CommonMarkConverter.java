package io.quarkiverse.qute.web.markdown.runtime.commonmark;

import java.util.ArrayList;
import java.util.List;

import org.commonmark.Extension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import io.quarkiverse.qute.web.markdown.runtime.MdConverter;

public class CommonMarkConverter implements MdConverter {

    private final Parser parser;
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public CommonMarkConverter() {
        this.parser = Parser.builder().build();
    }

    public CommonMarkConverter(List<Extension> extensions) {
        if (extensions == null) {
            extensions = new ArrayList<>();
        }
        this.parser = Parser.builder().extensions(extensions).build();
    }

    @Override
    public String html(String markdown) {
        Node node = parser.parse(markdown);
        return htmlRenderer.render(node);
    }
}
