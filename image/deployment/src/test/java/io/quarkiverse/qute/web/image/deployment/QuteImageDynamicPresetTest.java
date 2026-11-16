package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * The preset must be known at build time: a preset given by an expression fails the build instead of silently
 * converting with the default preset and missing the lookup at render time.
 */
public class QuteImageDynamicPresetTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("{#let p='retina'}{#img '/images/photo.png' preset=p/}{/let}"),
                            "web/templates/pub/dynamic-preset.html"))
            .assertException(t -> {
                String messages = QuteImageToProcessUnknownPresetTest.messages(t);
                assertTrue(messages.contains("The preset of {#img} in template 'pub/dynamic-preset.html' must be a"
                        + " string literal, got 'p'"), messages);
            });

    @Test
    public void testExpressionPresetFailsTheBuild() {
        // the assertion is in assertException
    }
}
