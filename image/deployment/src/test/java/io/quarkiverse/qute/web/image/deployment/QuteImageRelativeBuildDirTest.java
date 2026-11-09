package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A relative build dir is resolved against the build output directory.
 */
public class QuteImageRelativeBuildDirTest {

    static final Path REPORT = Path.of("target/output-dir.txt");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.build-dir", "qute-image-relative")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(new ReportStep())
                    .consumes(OutputTargetBuildItem.class)
                    .produces(ImagesDirBuildItem.class)
                    .produces(GeneratedResourceBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"));

    public static class ReportStep implements BuildStep {
        @Override
        public void execute(BuildContext context) {
            context.produce(ImagesDirBuildItem.of("web"));
            Path outputDir = context.consume(OutputTargetBuildItem.class).getOutputDirectory();
            try {
                Files.writeString(REPORT, outputDir.toAbsolutePath().toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(new GeneratedResourceBuildItem("META-INF/output-dir-test.txt",
                    outputDir.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void testRelativeBuildDirUnderOutputDirectory() throws IOException {
        Path buildDir = Path.of(Files.readString(REPORT)).resolve("qute-image-relative");
        assertTrue(Files.isDirectory(buildDir), "missing " + buildDir);
        try (Stream<Path> s = Files.walk(buildDir)) {
            assertTrue(s.anyMatch(p -> p.toString().endsWith(".jpg")), "no generated jpg under " + buildDir);
        }
    }
}
