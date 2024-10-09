package io.quarkiverse.qute.web.asciidoc.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;

@EngineConfiguration
public class AsciidocSectionHelperFactory implements SectionHelperFactory<AsciidocSectionHelperFactory.AsciidocSectionHelper> {
    private static final List<String> ASCIIDOC_SECTIONS = List.of("asciidoc", "ascii");

    @Override
    public List<String> getDefaultAliases() {
        return ASCIIDOC_SECTIONS;
    }

    @Override
    public AsciidocSectionHelper initialize(SectionInitContext context) {
        return new AsciidocSectionHelper();
    }

    public static class AsciidocSectionHelper implements SectionHelper {
        private final AsciidocConverter converter = new AsciidocConverter();

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                return CompletedStage.of(new SingleResultNode(converter.apply(sb.toString())));
            });
        }
    }
}
