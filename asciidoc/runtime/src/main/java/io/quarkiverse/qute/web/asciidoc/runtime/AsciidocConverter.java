package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.Map;

import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

public class AsciidocConverter {

    private final Parser parser = new Parser();
    private final AsciidoctorLikeHtmlRenderer renderer = new AsciidoctorLikeHtmlRenderer(
            new AsciidoctorLikeHtmlRenderer.Configuration()
                    .setAttributes(Map.of("noheader", "true")));

    public String apply(String asciidoc) {
        Body body = parser.parseBody(asciidoc, new Parser.ParserContext(null));
        renderer.visitBody(body);
        return renderer.result();
    }
}
