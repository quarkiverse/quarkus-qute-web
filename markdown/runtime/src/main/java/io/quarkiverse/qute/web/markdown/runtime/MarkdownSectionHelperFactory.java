package io.quarkiverse.qute.web.markdown.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.quarkiverse.qute.web.markdown.runtime.commonmark.CommonMarkConverter;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;

@EngineConfiguration
public class MarkdownSectionHelperFactory implements SectionHelperFactory<MarkdownSectionHelperFactory.MarkdownSectionHelper> {
    private static final List<String> MARKDOWN_SECTIONS = List.of("markdown", "md");

    @Override
    public List<String> getDefaultAliases() {
        return MARKDOWN_SECTIONS;
    }

    @Override
    public MarkdownSectionHelper initialize(SectionInitContext context) {
        return new MarkdownSectionHelper();
    }

    static class MarkdownSectionHelper implements SectionHelper {
        private final CommonMarkConverter converter = new CommonMarkConverter();

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
