package io.quarkiverse.qute.web.image.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.Expression;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Results;

@EngineConfiguration
public class ImageNamespaceResolver implements NamespaceResolver {

    @Inject
    Images images;

    @Inject
    Engine engine;

    @Inject
    ImageConfig config;

    @Override
    public String getNamespace() {
        return "img";
    }

    @Override
    public CompletionStage<Object> resolve(EvalContext context) {
        String method = context.getName();
        List<Expression> params = context.getParams();
        if (params.isEmpty()) {
            return CompletedStage.of(Results.NotFound.EMPTY);
        }
        return context.evaluate(params.get(0)).thenCompose(srcObj -> {
            CompletionStage<Object> presetStage = params.size() > 1
                    ? context.evaluate(params.get(1))
                    : CompletedStage.of("default");
            return presetStage.thenCompose(presetObj -> {
                String src = srcObj != null ? srcObj.toString() : null;
                String preset = presetObj != null ? presetObj.toString() : "default";
                String templateId = context.resolutionContext().getTemplate().getId();
                if (src == null || src.isBlank()) {
                    return CompletedStage.of("");
                }

                ImageTag imageTag = images.get(templateId, src, preset);
                if (imageTag == null) {
                    ImageSectionHelperFactory.checkMiss(config, src, preset, templateId);
                }

                return switch (method) {
                    case "path" -> resolvePath(imageTag, src);
                    case "tag" -> resolveTag(imageTag, src);
                    default -> CompletedStage.of(Results.NotFound.EMPTY);
                };
            });
        });
    }

    private CompletionStage<Object> resolvePath(ImageTag imageTag, String src) {
        if (imageTag == null) {
            return CompletedStage.of(src != null ? src : "");
        }
        return CompletedStage.of(ImageTemplateExtension.imgSrc(imageTag));
    }

    private CompletionStage<Object> resolveTag(ImageTag imageTag, String src) {
        if (imageTag == null) {
            return engine.getTemplate("qute-image/fallback-image.html")
                    .instance()
                    .data("src", src)
                    .data("imgAttrs", Collections.emptyMap())
                    .renderAsync()
                    .thenApply(html -> (Object) new RawString(html));
        }
        ImageAttrs attrs = ImageAttrs.from(Map.of(), imageTag);
        return engine.getTemplate("qute-image/image.html")
                .instance()
                .data("image", imageTag)
                .data("imgAttrs", attrs.imgMap())
                .data("pictureAttrs", attrs.pictureMap())
                .renderAsync()
                .thenApply(html -> (Object) new RawString(html));
    }
}
