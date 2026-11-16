package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/** A literal with a preset that is not configured fails the build, naming the usage and the presets that exist. */
public class QuteImageUnknownPresetTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web/static")
                    .addAsResource(new StringAsset("{#img '/static/images/white_1920_1080.png' preset='nope'/}"),
                            "web/templates/pub/preset.html"))
            .assertException(t -> {
                String messages = QuteImageToProcessUnknownPresetTest.messages(t);
                assertTrue(messages.contains("Unknown preset 'nope' in {#img} in template 'pub/preset.html'"), messages);
                assertTrue(messages.contains("small"), messages);
            });

    @Test
    public void testUnknownPresetFailsTheBuild() {
        // the assertion is in assertException
    }
}
