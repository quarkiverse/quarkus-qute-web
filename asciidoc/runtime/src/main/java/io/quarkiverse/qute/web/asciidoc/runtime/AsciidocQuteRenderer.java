package io.quarkiverse.qute.web.asciidoc.runtime;

import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.model.Header;
import io.yupiik.asciidoc.model.Listing;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

import java.util.LinkedHashSet;
import java.util.Map;

public class AsciidocQuteRenderer extends AsciidoctorLikeHtmlRenderer {
    private Map<String, String> specificAttributes;
    private LinkedHashSet<String> beforeBodyEndInjections = null;

    public AsciidocQuteRenderer(final Configuration configuration) {
        super(configuration);
    }

    @Override
    public void visitHeader(final Header header) {
        specificAttributes = header.attributes();
        super.visitHeader(header);
    }

    @Override
    public void visitListing(final Listing element) {
        if ("mermaid".equals(element.options().get(""))) {
            visitMermaid(element);
            return;
        }
        super.visitListing(element);
    }

    @Override
    public void visitBody(Body body) {
        try {
            super.visitBody(body);
        } finally {
            // we could use beforeBodyEnd() too but can be bypassed if visit(Document) is replaced by visitBody(Body)
            if (beforeBodyEndInjections != null) {
                builder.append(String.join("\n", beforeBodyEndInjections)).append('\n');
            }
        }
    }

    // here the idea is to just run mermaid.js, we do not want to depends on a 3rd party as asciidoctor-diagram
    protected void visitMermaid(final Listing element) {
        // note: should we call escape(), can break mermaid but not escaping can break html
        final var diagram = '\n' + element.value().strip() + '\n';
        builder.append("<pre");
        writeCommonAttributes(element.options(), c -> "mermaid" + (c != null ? ' ' + c : ""));
        builder.append(">");
        builder.append(diagram);
        builder.append("</pre>\n");

        // mermaid-skipModule attribute enables to let the injection be done with the layout template
        // (preferred in general since it can be bundled)
        // we do not disable the injection by default just to have it working by default
        if (!configuration.getAttributes().containsKey("mermaid-skipModule") &&
                (specificAttributes == null || !specificAttributes.containsKey("mermaid-skipModule"))) {
            if (beforeBodyEndInjections == null) {
                beforeBodyEndInjections = new LinkedHashSet<>();
            }
            beforeBodyEndInjections.add("<script type=\"module\">" +
                    "import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';" +
                    "</script>");
        }
    }
}
