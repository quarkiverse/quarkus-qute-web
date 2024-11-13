package io.quarkiverse.qute.web.asciidoc.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.qute.web.asciidoc.runtime.kroki.KrokiClient;
import io.quarkiverse.qute.web.asciidoc.runtime.kroki.KrokiConfig;
import io.quarkiverse.qute.web.asciidoc.runtime.kroki.PayloadEncoder;
import io.yupiik.asciidoc.model.Code;
import io.yupiik.asciidoc.model.Macro;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

public class AsciidoctorKrokiLikeHtmlRenderer extends AsciidoctorLikeHtmlRenderer {

    private final Set<String> supportedDiagramTypes = Set.of(
            "a2s", "blockdiag", "seqdiag", "actdiag", "nwdiag.", "bytefield", "dbml", "ditaa", "dpic", "erd.ad",
            "gnuplot", "graphviz", "lilypond", "mermaid", "msc", "nomnoml", "penrose", "pikchr", "pintora", "plantuml",
            "shaape", "smcat", "structurizr", "svgbob", "symbolator", "syntrax", "umlet", "vega", "wavedrom");

    private final KrokiClient krokiClient;
    private final KrokiConfig krokiConfig;

    public AsciidoctorKrokiLikeHtmlRenderer(Configuration configuration, KrokiClient krokiClient, KrokiConfig krokiConfig) {
        super(configuration);
        this.krokiClient = krokiClient;
        this.krokiConfig = krokiConfig;
        try {
            Path outputDir = Path.of(krokiConfig.outputDirBasePath());
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visitCode(Code element) {
        String maybeDiagramType = element.options().getOrDefault("", "");
        if (supportedDiagramTypes.contains(maybeDiagramType)) {

            String renderedImageFileName = renderDiagram(
                    maybeDiagramType,
                    element.options().getOrDefault("format", "svg"),
                    PayloadEncoder.encode(element.value()),
                    element.options().getOrDefault("target", "" + element.hashCode()));
            Map<String, String> options = new HashMap<>();
            options.put("alt", "%s diagram".formatted(renderedImageFileName));
            if (element.options().containsKey("role")) {
                options.put("role", element.options().get("role"));
            }
            Macro macro = new Macro(
                    "image",
                    Path.of(krokiConfig.imageBaseUrl(), renderedImageFileName).toString(),
                    options,
                    false);
            super.visitMacro(macro);
            return;
        }
        super.visitCode(element);
    }

    private String renderDiagram(String diagramType, String format, byte[] content, String name) {
        try {
            String data = krokiClient.convert(diagramType, format, new String(content, StandardCharsets.UTF_8));
            String fileName = "%s.%s".formatted(name, format);
            Path imagePath = Path.of(krokiConfig.outputDirBasePath(), fileName);
            Files.write(imagePath, data.getBytes());
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
