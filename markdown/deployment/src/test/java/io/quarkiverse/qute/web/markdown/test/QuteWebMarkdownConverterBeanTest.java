package io.quarkiverse.qute.web.markdown.test;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.markdown.runtime.MdConverter;
import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An {@link MdConverter} bean of the application replaces the CommonMark converter, for the section and for the
 * template extension methods alike.
 */
public class QuteWebMarkdownConverterBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(app -> app
                    .addClasses(SourceConverter.class)
                    .addAsResource(new StringAsset("{#markdown}# Qute{/markdown}"), "templates/converter.txt")
                    .addAsResource(new StringAsset("{text.markdownify}"), "templates/markdownify.txt"));

    @Inject
    Engine engine;

    @Test
    void converterBeanReplacesCommonMark() {
        Assertions.assertEquals("<pre class=\"markdown\"># Qute</pre>", engine.getTemplate("converter").render());
        Assertions.assertEquals("<pre class=\"markdown\"># Qute</pre>",
                engine.getTemplate("markdownify").data("text", "# Qute").render());
    }

    @Singleton
    public static class SourceConverter implements MdConverter {
        @Override
        public String html(String text) {
            return "<pre class=\"markdown\">" + text + "</pre>";
        }
    }
}
