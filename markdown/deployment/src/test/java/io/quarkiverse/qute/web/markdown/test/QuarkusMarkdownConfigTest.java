package io.quarkiverse.qute.web.markdown.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.Comparator;
import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuarkusMarkdownConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addAsResource(new StringAsset("{data.markdownify}"), "templates/foo.txt")
                            .addAsResource(new StringAsset("{data.mdToHtml}"), "templates/bar.txt")
                            .addAsResource(new StringAsset("quarkus.qute.web.markdown.plugin.heading-anchor.enabled=false\n" +
                                    "quarkus.qute.web.markdown.plugin.autolink.enabled=false"), "application.properties"));

    @Inject
    Instance<Extension> extensions;

    @Inject
    Engine engine;

    @Test
    void shouldKeepOnlyTheEnabledExtensions() {
        List<Class<?>> list = extensions.stream().<Class<?>> map(Extension::getClass)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
        Assertions.assertEquals(List.of(AlertsExtension.class, TablesExtension.class), list);
    }

    @Test
    void shouldNotAnchorHeadingsNorLinkUrls() {
        String md = """
                # Qute

                See https://quarkus.io
                """;
        for (String template : List.of("foo", "bar")) {
            String result = engine.getTemplate(template).data("data", md).render();
            assertThat(result, containsString("<h1>Qute</h1>"));
            assertThat(result, containsString("See https://quarkus.io"));
            assertThat(result, not(containsString("<a href")));
        }
    }
}
