package io.quarkiverse.qute.web.markdown.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuarkusMarkdownAlertsDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addAsResource(new StringAsset("{data.markdownify}"), "templates/alert.txt")
                            .addAsResource(new StringAsset(
                                    "quarkus.qute.web.markdown.plugin.alerts.enabled=false"),
                                    "application.properties"));

    @Inject
    Engine engine;

    @Test
    void shouldRenderAsBlockquoteWhenDisabled() {
        String md = """
                > [!NOTE]
                > This is a note.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("<blockquote"));
        assertThat(result, not(containsString("markdown-alert")));
    }
}
