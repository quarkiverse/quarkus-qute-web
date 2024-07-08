package io.quarkiverse.qute.web.markdown.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;

public class QuteMarkdownSectionHelperTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app.addAsResource(new StringAsset(
                            "{#markdown}# Qute and Roq{/markdown}"),
                            "templates/foo.txt"));

    @Inject
    Template foo;

    @Test
    void testJsonObjectValueResolver() {
        String result = foo.render();

        System.out.println(result);

        Assertions.assertEquals("<h1>Qute and Roq</h1>\n", result);
    }
}
