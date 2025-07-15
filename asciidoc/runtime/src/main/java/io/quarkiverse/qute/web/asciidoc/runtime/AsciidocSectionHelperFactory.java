package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.List;
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

    private static final LazyValue<AsciidocConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(AsciidocConverter.class).get());

    private final AsciidocConverter converter;
    private final AsciidocSectionHelper helper = new AsciidocSectionHelper();

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
    public AsciidocSectionHelper initialize(SectionInitContext context) {
        return helper;
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text, String ignoredName,
            @TemplateExtension.TemplateAttribute(SOURCE_PATH) Object templatePath,
            @TemplateExtension.TemplateAttribute(SITE_PATH) Object sitePath,
            @TemplateExtension.TemplateAttribute(SITE_URL) Object siteUrl,
            @TemplateExtension.TemplateAttribute(PAGE_PATH) Object pagePath,
            @TemplateExtension.TemplateAttribute(PAGE_URL) Object pageUrl) {
        return new RawString(CONVERTER.get().apply(text, new AsciidocConverter.TemplateAttributes((String) templatePath,
                (String) siteUrl, (String) sitePath, (String) pageUrl, (String) pagePath)));
    }

    public class AsciidocSectionHelper implements SectionHelper {

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                final ResolutionContext resolutionContext = context.resolutionContext();
                final AsciidocConverter.TemplateAttributes attributes = new AsciidocConverter.TemplateAttributes(
                        (String) resolutionContext.getAttribute(SOURCE_PATH),
                        (String) resolutionContext.getAttribute(SITE_URL),
                        (String) resolutionContext.getAttribute(SITE_PATH),
                        (String) resolutionContext.getAttribute(PAGE_URL),
                        (String) resolutionContext.getAttribute(PAGE_PATH));
                return CompletedStage.of(new SingleResultNode(
                        converter.apply(sb.toString(), attributes)));
            });
        }
    }

}
