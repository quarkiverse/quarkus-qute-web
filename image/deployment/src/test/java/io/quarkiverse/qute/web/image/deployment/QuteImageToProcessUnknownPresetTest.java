package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

public class QuteImageToProcessUnknownPresetTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> {
                        context.produce(ImagesDirBuildItem.of("web"));
                        context.produce(QuteImageToProcessBuildItem.of("/x.png", "nope",
                                java.nio.file.Path.of("target/test-classes/roq-public/Foé_1920_1081.png")));
                    })
                    .produces(ImagesDirBuildItem.class)
                    .produces(QuteImageToProcessBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"))
            .assertException(t -> assertThat(messages(t), containsString("Unknown preset 'nope'")));

    private static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    public void testUnknownPresetFails() {
        // the assertion is in assertException: the build must fail naming the preset
    }
}
