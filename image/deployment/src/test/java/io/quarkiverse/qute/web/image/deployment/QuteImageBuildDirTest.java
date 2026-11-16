package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The build dir is persistent: a build must neither wipe it nor fail when it already holds files, so that
 * generated variants survive across builds and dev mode restarts.
 */
public class QuteImageBuildDirTest {

    static final Path BUILD_DIR = Path.of("target/qute-image-keep").toAbsolutePath();

    static {
        try {
            Files.createDirectories(BUILD_DIR);
            Files.writeString(BUILD_DIR.resolve("keep.txt"), "keep me");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.build-dir", BUILD_DIR.toString())
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"));

    @Test
    public void testBuildDirIsNotWiped() throws IOException {
        assertTrue(Files.exists(BUILD_DIR.resolve("keep.txt")), "keep.txt was deleted");
        try (Stream<Path> s = Files.walk(BUILD_DIR)) {
            assertTrue(s.anyMatch(p -> p.toString().endsWith(".jpg")), "no generated jpg under " + BUILD_DIR);
        }
    }
}
