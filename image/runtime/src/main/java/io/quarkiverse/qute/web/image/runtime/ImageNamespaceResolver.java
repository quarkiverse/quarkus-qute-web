package io.quarkiverse.qute.web.image.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkiverse.qute.web.image.runtime.model.ImageLookup;
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
import io.quarkus.qute.TemplateException;
import io.quarkus.qute.UserTagSectionHelper.Arguments;

@EngineConfiguration
public class ImageNamespaceResolver implements NamespaceResolver {

    /**
     * Explicitly high so that a host's fallback resolver for the {@code img} namespace, left at Qute's default
     * priority, is only asked when this one answers {@code NotFound}; a host resolver that must win uses a higher
     * value.
     */
    public static final int PRIORITY = 100;

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
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public CompletionStage<Object> resolve(EvalContext context) {
        String method = context.getName();
        List<Expression> params = context.getParams();
        if (params.isEmpty()) {
            return CompletedStage.of(Results.NotFound.EMPTY);
        }
        String templateId = ImageUtils.templateId(context.resolutionContext().getTemplate());
        return context.evaluate(params.get(0)).thenCompose(srcObj -> {
            CompletionStage<Object> presetStage = params.size() > 1 ? context.evaluate(params.get(1))
                    : CompletedStage.of("default");
            return presetStage.thenCompose(presetObj -> {
                String src = srcObj == null || srcObj.toString().isBlank() ? null : srcObj.toString();
                String preset = presetObj != null ? presetObj.toString() : "default";
                return switch (method) {
                    // {#img}: the source, the preset and the tag's other arguments, the attributes
                    case "lookup" -> context.evaluate(params.get(2))
                            .thenApply(args -> lookup(templateId, src, preset, (Arguments) args));
                    case "path" -> CompletedStage.of(src == null ? "" : path(templateId, src, preset));
                    // the markup of {#img}, looked up for the calling template: a new template instance would
                    // report its own id
                    case "tag" -> src == null ? CompletedStage.of("")
                            : engine.getTemplate("qute-image/img-markup").instance()
                                    .data("tag", lookup(templateId, src, preset, List.of())).renderAsync()
                                    .thenApply(RawString::new);
                    default -> CompletedStage.of(Results.NotFound.EMPTY);
                };
            });
        });
    }

    private String path(String templateId, String src, String preset) {
        ImageTag imageTag = lookup(templateId, src, preset);
        return imageTag != null ? ImageTemplateExtension.imgSrc(imageTag) : src;
    }

    private ImageLookup lookup(String templateId, String src, String preset,
            Iterable<Map.Entry<String, Object>> attrs) {
        ImageTag imageTag = src == null ? null : lookup(templateId, src, preset);
        return new ImageLookup(src, imageTag, ImageAttrs.from(attrs, imageTag));
    }

    /**
     * @return the tag registered for the source in the template, or null
     * @throws TemplateException in strict mode for a non-remote source that was not converted
     */
    private ImageTag lookup(String templateId, String src, String preset) {
        ImageTag imageTag = images.get(templateId, src, preset);
        if (imageTag == null && !ImageUtils.isRemote(src) && config.strict()) {
            throw new TemplateException(("Image not found for src '%s' with preset '%s' in template '%s'"
                    + " (quarkus.qute.image.strict=true). Add the image to a scanned directory or produce it at"
                    + " build time.").formatted(src, preset, templateId));
        }
        return imageTag;
    }
}
