package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;
import io.quarkus.qute.TemplateExtension;

@EngineConfiguration
public class AsciidocSectionHelperFactory
        implements SectionHelperFactory<AsciidocSectionHelperFactory.AsciidocSectionHelper> {

    private static final LazyValue<AsciidocConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(AsciidocConverter.class).get());

    private final AsciidocSectionHelper HELPER = new AsciidocSectionHelper();

    @Override
    public List<String> getDefaultAliases() {
        return List.of("asciidoc", "ascii");
    }

    @Override
    public AsciidocSectionHelper initialize(SectionInitContext context) {
        return HELPER;
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static String convertToAsciidoc(String text, String ignoredName) {
        return CONVERTER.get().apply(text);
    }

    public static class AsciidocSectionHelper implements SectionHelper {

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                return CompletedStage.of(new SingleResultNode(CONVERTER.get().apply(sb.toString())));
            });
        }
    }
}
