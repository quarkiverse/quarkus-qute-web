package io.quarkiverse.qute.web.markdown.test;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;

public class QuteMarkdownSectionWithInnerSectionTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app.addAsResource(new StringAsset(
                            """
                                    <h1>Quarkus and Qute</h1>
                                    {#md}
                                    # Qute and Roq
                                    Here is a list:
                                    {#for item in items}
                                    - an {item} as a list item
                                    {/for}
                                    {/md}
                                    """),
                            "templates/foo.txt"));

    @Inject
    Template foo;

    @Test
    void testJsonObjectValueResolver() {
        String result = foo.data("items", List.of("apple", "banana", "cherry"))
                .render();

        Assertions.assertEquals("""
                <h1>Quarkus and Qute</h1>
                <h1>Qute and Roq</h1>
                <p>Here is a list:</p>
                <ul>
                <li>an apple as a list item</li>
                <li>an banana as a list item</li>
                <li>an cherry as a list item</li>
                </ul>
                """, result);

    }
}
