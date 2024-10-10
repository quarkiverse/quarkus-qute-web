package io.quarkiverse.qute.web.asciidoc.test;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;

public class QuteExtensionsTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app.addAsResource(new StringAsset(
                            "{data.asciidocify}"),
                            "templates/foo.txt"));

    @Inject
    Template foo;

    @Test
    void shouldUseAsciidocify() {

        String rendered = foo.data("data", "=Qute and Roq").render();

        Assertions.assertThat(rendered).contains("<h1>Qute and Roq</h1>");
    }
}
