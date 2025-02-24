package io.quarkiverse.qute.web.markdown.test;

import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.markdown.runtime.AutolinkConfiguration;
import io.quarkiverse.qute.web.markdown.runtime.HeadingAnchorConfiguration;
import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuteWebMarkdownExtensionsTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addClasses(AutolinkConfiguration.class, HeadingAnchorConfiguration.class)
                            .addAsResource(new StringAsset("{data.markdownify}"), "templates/foo.txt")
                            .addAsResource(new StringAsset("{data.mdToHtml}"), "templates/bar.txt")
                            .addAsResource(new StringAsset(""), "application.properties"));

    @Inject
    Engine engine;

    @Test
    void shouldUseMarkdownify() {
        String md = """
                # My first title
                ## My second title
                ### My last title
                """;
        assertMdResult(engine.getTemplate("foo").data("data", md).render());
        assertMdResult(engine.getTemplate("bar").data("data", md).render());
    }

    private void assertMdResult(String result) {
        assertThat(result, Matchers.containsString("<h1 id=\"my-first-title\">My first title</h1>"));
        assertThat(result, Matchers.containsString("<h2 id=\"my-second-title\">My second title</h2>"));
        assertThat(result, Matchers.containsString("<h3 id=\"my-last-title\">My last title</h3>"));
    }
}
