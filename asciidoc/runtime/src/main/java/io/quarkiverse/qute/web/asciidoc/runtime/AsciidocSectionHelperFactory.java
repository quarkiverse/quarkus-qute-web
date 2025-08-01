package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.*;

@EngineConfiguration
public class AsciidocSectionHelperFactory
        implements SectionHelperFactory<AsciidocSectionHelperFactory.AsciidocSectionHelper> {

    public static final String SOURCE_PATH = "sourcePath";
    public static final String PAGE_PATH = "pagePath";
    public static final String PAGE_URL = "pageUrl";
    public static final String SITE_PATH = "sitePath";
    public static final String SITE_URL = "siteUrl";

    private static final String ASCIIDOC_ATTRIBUTES = "attributes";

    private static final LazyValue<AsciidocConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(AsciidocConverter.class).get());

    private final AsciidocConverter converter;

    public AsciidocSectionHelperFactory() {
        this.converter = null;
    }

    @Inject
    public AsciidocSectionHelperFactory(AsciidocConverter converter) {
        this.converter = converter;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of("asciidoc", "ascii");
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder().addParameter(Parameter.builder(ASCIIDOC_ATTRIBUTES).optional().build())
                .checkNumberOfParams(false).build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        if (block.hasParameter(ASCIIDOC_ATTRIBUTES)) {
            block.addExpression(ASCIIDOC_ATTRIBUTES, block.getParameter(ASCIIDOC_ATTRIBUTES));
        }
        return outerScope;
    }

    @Override
    public AsciidocSectionHelper initialize(SectionInitContext c) {
        final Expression asciidocAttributesExpr = c.getExpression(ASCIIDOC_ATTRIBUTES);
        return new AsciidocSectionHelper(asciidocAttributesExpr);
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text,
            String ignoredName,
            @TemplateExtension.TemplateAttribute(SOURCE_PATH) Object templatePath,
            @TemplateExtension.TemplateAttribute(SITE_PATH) Object sitePath,
            @TemplateExtension.TemplateAttribute(SITE_URL) Object siteUrl,
            @TemplateExtension.TemplateAttribute(PAGE_PATH) Object pagePath,
            @TemplateExtension.TemplateAttribute(PAGE_URL) Object pageUrl) {
        return convertToAsciidoc(text, ignoredName, Map.of(), templatePath, sitePath, siteUrl, pagePath, pageUrl);
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text,
            String ignoredName,
            Map<String, Object> attributes,
            @TemplateExtension.TemplateAttribute(SOURCE_PATH) Object templatePath,
            @TemplateExtension.TemplateAttribute(SITE_PATH) Object sitePath,
            @TemplateExtension.TemplateAttribute(SITE_URL) Object siteUrl,
            @TemplateExtension.TemplateAttribute(PAGE_PATH) Object pagePath,
            @TemplateExtension.TemplateAttribute(PAGE_URL) Object pageUrl) {
        return new RawString(
                CONVERTER.get().apply(text, convertToStringMap(attributes),
                        new AsciidocConverter.TemplateAttributes((String) templatePath, (String) siteUrl,
                                (String) sitePath, (String) pageUrl, (String) pagePath)));
    }

    public class AsciidocSectionHelper implements SectionHelper {
        private final Expression asciidocAttributesExpr;

        public AsciidocSectionHelper(Expression asciidocAttributesExpr) {
            this.asciidocAttributesExpr = asciidocAttributesExpr;
        }

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            if (asciidocAttributesExpr != null) {
                return context.evaluate(asciidocAttributesExpr)
                        .thenCompose(a -> execute(a, context));
            }
            return execute(null, context);
        }

        public CompletionStage<ResultNode> execute(Object a, SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                Map<String, String> asciidocAttributes = Map.of();
                if (a instanceof Map) {
                    asciidocAttributes = convertToStringMap((Map<?, ?>) a);
                }
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                final ResolutionContext resolutionContext = context.resolutionContext();
                final AsciidocConverter.TemplateAttributes templateAttributes = new AsciidocConverter.TemplateAttributes(
                        (String) resolutionContext.getAttribute(SOURCE_PATH),
                        (String) resolutionContext.getAttribute(SITE_URL),
                        (String) resolutionContext.getAttribute(SITE_PATH),
                        (String) resolutionContext.getAttribute(PAGE_URL),
                        (String) resolutionContext.getAttribute(PAGE_PATH));
                return CompletedStage.of(new SingleResultNode(
                        converter.apply(sb.toString(), asciidocAttributes, templateAttributes)));
            });
        }
    }

    public static Map<String, String> convertToStringMap(Map<?, ?> a) {
        Map<String, String> map = new HashMap<>();
        a.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return map;
    }

}
