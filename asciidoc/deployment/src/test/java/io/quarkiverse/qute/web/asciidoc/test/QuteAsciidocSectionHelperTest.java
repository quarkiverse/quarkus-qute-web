package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;

public class QuteAsciidocSectionHelperTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app.addAsResource(new StringAsset(
                            "{#ascii}= Qute and Roq{/ascii}"),
                            "templates/foo.txt"));

    @Inject
    Template foo;

    @Test
    void shouldConvertUsingAsciiTag() {
        String result = foo.render();

        assertThat(result).isEqualToIgnoringWhitespace("<h1>Qute and Roq</h1>");
    }
}
