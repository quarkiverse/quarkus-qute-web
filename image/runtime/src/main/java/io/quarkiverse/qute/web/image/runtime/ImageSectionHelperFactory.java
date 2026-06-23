package io.quarkiverse.qute.web.image.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.Expression;
import io.quarkus.qute.Parameter;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.Scope;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SectionNode;
import io.quarkus.qute.Template;

@EngineConfiguration
public class ImageSectionHelperFactory implements SectionHelperFactory<SectionHelper> {

    private static final Set<String> RESERVED_PARAMS = Set.of("src", "preset");
    private static final String IMAGE_TEMPLATE = "qute-image/image.html";
    private static final String FALLBACK_TEMPLATE = "qute-image/fallback-image.html";

    @Inject
    Images images;

    @Inject
    Instance<DynamicImageResolver> dynamicResolver;

    public ImageSectionHelperFactory() {
        images = null;
    }

    public ImageSectionHelperFactory(Images images) {
        this.images = images;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of("qimg");
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder()
                .addParameter("src")
                .addParameter(Parameter.builder("preset").defaultValue("'default'").build())
                .checkNumberOfParams(false)
                .build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        if (!block.getLabel().equals("$main")) {
            return outerScope;
        }
        for (Map.Entry<String, String> entry : block.getParameters().entrySet()) {
            block.addExpression(entry.getKey(), entry.getValue());
        }
        return outerScope;
    }

    @Override
    public SectionHelper initialize(SectionInitContext context) {
        Expression srcExpr = context.getExpression("src");
        Expression presetExpr = context.getExpression("preset");
        Map<String, Expression> attrExpressions = new HashMap<>();
        for (String key : context.getParameters().keySet()) {
            if (!RESERVED_PARAMS.contains(key)) {
                attrExpressions.put(key, context.getExpression(key));
            }
        }
        final Engine engine = context.getEngine();
        final SectionNode imageRoot = getTemplateRoot(engine, IMAGE_TEMPLATE);
        final SectionNode fallbackRoot = getTemplateRoot(engine, FALLBACK_TEMPLATE);

        return new SectionHelper() {
            @Override
            public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
                Map<String, Expression> toEval = new HashMap<>(attrExpressions);
                toEval.put("src", srcExpr);
                toEval.put("preset", presetExpr);
                return context.evaluate(toEval)
                        .thenCompose(resolved -> {
                            Object srcObj = resolved.get("src");
                            String src = srcObj != null ? srcObj.toString() : null;
                            String preset = resolved.get("preset") != null
                                    ? resolved.get("preset").toString()
                                    : "default";
                            ImageTag imageTag = images.get(
                                    context.resolutionContext().getTemplate().getId(), src, preset);
                            if (imageTag == null && dynamicResolver != null && dynamicResolver.isResolvable()) {
                                imageTag = dynamicResolver.get().resolve(src, preset);
                            }
                            Map<String, String> attrs = new HashMap<>();
                            for (Map.Entry<String, Object> entry : resolved.entrySet()) {
                                if (!RESERVED_PARAMS.contains(entry.getKey()) && entry.getValue() != null) {
                                    attrs.put(entry.getKey(), entry.getValue().toString());
                                }
                            }
                            ImageAttrs imageAttrs = ImageAttrs.from(attrs, imageTag);
                            if (imageTag == null) {
                                Map<String, Object> fallbackData = new HashMap<>();
                                fallbackData.put("src", src);
                                fallbackData.put("imgAttrs", imageAttrs.imgMap());
                                return fallbackRoot.resolve(
                                        context.newResolutionContext(fallbackData, null));
                            }
                            Map<String, Object> data = new HashMap<>();
                            data.put("image", imageTag);
                            data.put("imgAttrs", imageAttrs.imgMap());
                            data.put("pictureAttrs", imageAttrs.pictureMap());
                            return imageRoot.resolve(
                                    context.newResolutionContext(data, null));
                        });
            }
        };
    }

    private static SectionNode getTemplateRoot(Engine engine, String templateId) {
        Template template = engine.getTemplate(templateId);
        if (template == null) {
            throw new IllegalStateException("Template not found: " + templateId);
        }
        return template.getRootNode();
    }
}
