package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A site with thousands of images must build: the processed maps are too large for the bytecode recorder's
 * constant pool, so they are recorded as a resource.
 */
public class QuteImageLargeSiteTest {

    static final int COUNT = 10_000;
    static final Path DIR = Path.of("target/qute-image-many").toAbsolutePath();

    static {
        try {
            Files.createDirectories(DIR);
            for (int i = 0; i < COUNT; i++) {
                Path file = DIR.resolve("img-" + i + ".png");
                if (!Files.exists(file)) {
                    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                    image.setRGB(0, 0, i); // distinct content, hence a distinct digest per image
                    ImageIO.write(image, "png", file.toFile());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.qute.image.presets.tiny.formats", "jpg")
            .overrideConfigKey("quarkus.qute.image.presets.tiny.widths", "2")
            .overrideConfigKey("quarkus.qute.image.generate[0].glob", "**.png")
            .overrideConfigKey("quarkus.qute.image.generate[0].presets", "tiny")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("many", DIR)))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    Images images;

    @Test
    public void testThousandsOfImagesAreRecorded() {
        assertNotNull(images.get(null, "/many/img-0.png", "tiny"));
        assertNotNull(images.get(null, "/many/img-" + (COUNT - 1) + ".png", "tiny"));
    }
}
