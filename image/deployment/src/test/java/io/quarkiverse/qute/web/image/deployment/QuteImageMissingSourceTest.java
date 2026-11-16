package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/** A literal pointing at a file that does not exist fails the build, naming the path and where it was looked up. */
public class QuteImageMissingSourceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web/static")
                    .addAsResource(new StringAsset("{#img '/static/images/nope.png'/}"), "web/templates/pub/missing.html"))
            .assertException(t -> {
                String messages = QuteImageToProcessUnknownPresetTest.messages(t);
                assertTrue(messages.contains("Image does not exist or is not a file: /static/images/nope.png"), messages);
            });

    @Test
    public void testMissingSourceFailsTheBuild() {
        // the assertion is in assertException
    }
}
