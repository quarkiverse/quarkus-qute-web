package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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
                    app -> app
                            .addAsResource(new StringAsset(
                                    "{#ascii}= Qute and Roq{/ascii}"),
                                    "templates/foo.txt")
                            .addAsResource(new StringAsset(
                                    """
                                            {#ascii attributes}
                                            = Qute and Roq

                                            \\{bar}
                                            {/ascii}
                                            """),
                                    "templates/bar.txt"));

    @Inject
    Template foo;

    @Inject
    Template bar;

    @Test
    void shouldConvertUsingAsciiTag() {
        String result = foo.render();

        assertThat(result).isEqualToIgnoringWhitespace("<h1>Qute and Roq</h1>");
    }

    @Test
    void shouldConvertUsingAsciiTagAndUseAttributes() {
        String result = bar.render(Map.of("attributes", Map.of("bar", "Qute and Asciidoc", "notitle", "true")));

        assertThat(result).isEqualToIgnoringWhitespace("""
                <div class="paragraph">
                 <p>Qute and Asciidoc</p>
                 </div>
                """);
    }
}
