package io.quarkiverse.qute.web.markdown.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.markdown.runtime.AutolinkConfiguration;
import io.quarkiverse.qute.web.markdown.runtime.HeadingAnchorConfiguration;
import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;

public class QuteMarkdownSectionHelperTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addClasses(AutolinkConfiguration.class, HeadingAnchorConfiguration.class)
                            .addAsResource(new StringAsset(
                                    "{#markdown}# Qute and Roq{/markdown}"),
                                    "templates/foo.txt"));

    @Inject
    Template foo;

    @Test
    void shouldConvertToHtmlCorrectly() {
        String result = foo.render();

        System.out.println(result);

        Assertions.assertEquals("<h1 id=\"qute-and-roq\">Qute and Roq</h1>\n", result);
    }
}
