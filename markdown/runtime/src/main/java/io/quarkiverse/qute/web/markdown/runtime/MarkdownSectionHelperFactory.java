package io.quarkiverse.qute.web.markdown.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.RawString;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;
import io.quarkus.qute.TemplateExtension;

@EngineConfiguration
public class MarkdownSectionHelperFactory
        implements SectionHelperFactory<MarkdownSectionHelperFactory.MarkdownSectionHelper> {

    private final MdConverter converter;

    public MarkdownSectionHelperFactory() {
        // This constructor is only used during build
        // where the converter is not used at all
        this.converter = null;
    }

    @Inject
    public MarkdownSectionHelperFactory(MdConverter mdConverter) {
        this.converter = mdConverter;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of("markdown", "md");
    }

    @Override
    public MarkdownSectionHelper initialize(SectionInitContext context) {
        return new MarkdownSectionHelper(converter);
    }

    // Lazily initialized converter used for the convertToMarkdown() template extention method
    private static final LazyValue<MdConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(MdConverter.class).get());

    @TemplateExtension(matchNames = { "markdownify", "mdToHtml" })
    static RawString convertToMarkdown(String text, String ignoredName) {
        return new RawString(CONVERTER.get().html(text));
    }

    public static class MarkdownSectionHelper implements SectionHelper {

        private final MdConverter converter;

        public MarkdownSectionHelper(MdConverter converter) {
            this.converter = converter;
        }

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                return CompletedStage.of(new SingleResultNode(converter.html(sb.toString())));
            });
        }
    }
}
