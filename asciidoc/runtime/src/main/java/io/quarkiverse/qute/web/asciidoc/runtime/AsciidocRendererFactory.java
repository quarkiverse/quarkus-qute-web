package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.qute.web.asciidoc.runtime.kroki.KrokiClient;
import io.quarkiverse.qute.web.asciidoc.runtime.kroki.KrokiConfig;
import io.yupiik.asciidoc.renderer.html.AsciidoctorLikeHtmlRenderer;

@ApplicationScoped
public class AsciidocRendererFactory {

    private final AsciidoctorKrokiLikeHtmlRenderer.Configuration rendererConfig = new AsciidoctorKrokiLikeHtmlRenderer.Configuration()
            .setAttributes(Map.of("noheader", "true"));
    private final KrokiClient krokiClient;
    private final KrokiConfig krokiConfig;
    private final QuteWebAsciidocBuildTimeConfig config;

    public AsciidocRendererFactory(@RestClient Instance<KrokiClient> krokiClient, KrokiConfig krokiConfig,
            QuteWebAsciidocBuildTimeConfig config) {
        this.config = config;
        this.krokiConfig = krokiConfig;
        if (this.config.prerenderDiagram()) {
            this.krokiClient = krokiClient.get();
        } else {
            this.krokiClient = null;
        }
    }

    public AsciidoctorLikeHtmlRenderer getRenderer() {
        if (config.prerenderDiagram()) {
            return new AsciidoctorKrokiLikeHtmlRenderer(rendererConfig, krokiClient, krokiConfig);
        }
        return new AsciidoctorLikeHtmlRenderer(rendererConfig);
    }
}
