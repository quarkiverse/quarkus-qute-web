package io.quarkiverse.qute.web.markdown.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuteWebMarkdownAlertsTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addAsResource(new StringAsset("{data.markdownify}"), "templates/alert.txt")
                            .addAsResource(new StringAsset(
                                    "quarkus.qute.web.markdown.plugin.alerts.custom-types.INFO=Information"),
                                    "application.properties"));

    @Inject
    Engine engine;

    @Test
    void shouldRenderNoteAlert() {
        String md = """
                > [!NOTE]
                > This is a note.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("markdown-alert-note"));
    }

    @Test
    void shouldRenderTipAlert() {
        String md = """
                > [!TIP]
                > This is a tip.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("markdown-alert-tip"));
    }

    @Test
    void shouldRenderImportantAlert() {
        String md = """
                > [!IMPORTANT]
                > This is important.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("markdown-alert-important"));
    }

    @Test
    void shouldRenderWarningAlert() {
        String md = """
                > [!WARNING]
                > This is a warning.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("markdown-alert-warning"));
    }

    @Test
    void shouldRenderCautionAlert() {
        String md = """
                > [!CAUTION]
                > This is a caution.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("markdown-alert-caution"));
    }

    @Test
    void shouldRenderCustomAlertType() {
        String md = """
                > [!INFO]
                > This is custom info.
                """;
        String result = engine.getTemplate("alert").data("data", md).render();
        assertThat(result, containsString("markdown-alert-info"));
        assertThat(result, containsString("Information"));
    }
}
