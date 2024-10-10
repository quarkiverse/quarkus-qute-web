package io.quarkiverse.qute.web.markdown.test;

import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;

public class QuteMarkdownExtensionsTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app.addAsResource(new StringAsset(
                            "{data.markdownify}"),
                            "templates/foo.txt"));

    @Inject
    Template foo;

    @Test
    void shouldUseMarkdownify() {
        String result = foo.data("data", """
                # My first title
                ## My second title
                ### My last title
                """)
                .render();

        assertThat(result, Matchers.containsString("<h1>My first title</h1>"));
        assertThat(result, Matchers.containsString("<h2>My second title</h2>"));
        assertThat(result, Matchers.containsString("<h3>My last title</h3>"));
    }
}
