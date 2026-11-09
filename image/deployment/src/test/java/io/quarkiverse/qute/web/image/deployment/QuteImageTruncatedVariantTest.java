package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageBuilder;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * A variant left empty by an interrupted build is regenerated instead of being reused. The empty file is seeded by
 * a build step that runs before the processor (it produces the image the processor consumes), using the real
 * output path derivation so the test follows it. The step records the variant path in a file under target/ since
 * the test method runs in another classloader and cannot use the deployment classes itself.
 */
public class QuteImageTruncatedVariantTest {

    static final Path BUILD_DIR = Path.of("target/qute-image-truncated").toAbsolutePath();
    static final String SEED_PATH = "/virtual/seed.png";
    static final String SEED_RESOURCE = "web/static/images/white_1920_1080.png";
    static final Path REPORT = Path.of("target/truncated-variant.txt");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.build-dir", BUILD_DIR.toString())
            .addBuildChainCustomizer(builder -> {
                builder.addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                        .produces(ImagesDirBuildItem.class)
                        .build();
                builder.addBuildStep(new SeedStep())
                        .consumes(QuteImageTargetDirBuildItem.class)
                        .produces(QuteImageToProcessBuildItem.class)
                        .build();
            })
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"));

    public static class SeedStep implements BuildStep {
        @Override
        public void execute(BuildContext context) {
            Path buildDir = context.consume(QuteImageTargetDirBuildItem.class).path;
            byte[] bytes = seedBytes();
            String variantPath = variantPath(bytes);
            Path truncated = buildDir.resolve(variantPath.substring(1));
            try {
                Files.createDirectories(truncated.getParent());
                Files.write(truncated, new byte[0]);
                Files.writeString(REPORT, variantPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(QuteImageToProcessBuildItem.of(SEED_PATH, "small", "seed.png", bytes));
        }
    }

    @Test
    public void testTruncatedVariantIsRegenerated() throws IOException {
        String variantPath = Files.readString(REPORT);
        Path variant = BUILD_DIR.resolve(variantPath.substring(1));
        assertTrue(Files.exists(variant), "expected the seeded variant at " + variant);
        assertThat("an empty variant must be regenerated, not reused", Files.size(variant), greaterThan(0L));
        byte[] served = RestAssured.get(variantPath).then().statusCode(200).extract().asByteArray();
        assertThat(served.length, greaterThan(0));
    }

    /**
     * The 320 wide jpg of the {@code small} preset (quality 70, ImageIO), derived by the same builder the
     * processor uses.
     */
    static String variantPath(byte[] bytes) {
        ImageId id = new ImageId(ImageUtils.digest(bytes), "seed", "png");
        String[] path = new String[1];
        new ImageBuilder(id).addGeneratedImage("/static/images/generated",
                new GeneratedImageOptions(320, 180, "image/jpeg", null, 70, "imageio"), true,
                generated -> path[0] = generated.outputPath());
        return path[0];
    }

    static byte[] seedBytes() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(SEED_RESOURCE)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
