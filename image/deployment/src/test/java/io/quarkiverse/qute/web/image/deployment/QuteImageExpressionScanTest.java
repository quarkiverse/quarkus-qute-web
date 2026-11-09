package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.stringContainsInOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageExpressionBuildItem;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The test JVM and the augmentation classloader do not share statics, so the build step writes
 * what it consumed to a file under target/ and the test reads it back.
 */
public class QuteImageExpressionScanTest {

    static final Path REPORT = Path.of("target/qute-image-expressions.txt");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> {
                // the images dir is consumed before the template analysis, so it must come from its own step
                builder.addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                        .produces(ImagesDirBuildItem.class)
                        .build();
                builder.addBuildStep(new ReportStep())
                        .consumes(QuteImageExpressionBuildItem.class)
                        .produces(GeneratedResourceBuildItem.class)
                        .build();
            })
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web"));

    public static class ReportStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            String report = context.consumeMulti(QuteImageExpressionBuildItem.class).stream()
                    .map(i -> String.join("|", i.templateId(), i.srcExpression(), i.presetExpression(),
                            String.valueOf(i.rootTypeInfo()), i.origin()))
                    .sorted()
                    .collect(Collectors.joining("\n"));
            try {
                Files.writeString(REPORT, report, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(new GeneratedResourceBuildItem("META-INF/qute-image-expressions-test.txt",
                    report.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void testExpressionsAreReported() throws IOException {
        // dynamic.html (used by other tests) also has a non-literal src; only look at our template
        List<String> lines = Files.readAllLines(REPORT).stream()
                .filter(l -> l.startsWith("pub/expressions.html|")).toList();
        assertThat(lines, hasSize(2));
        String sectionLine = lines.stream().filter(l -> l.contains("{#img}")).findFirst().orElseThrow();
        String nsLine = lines.stream().filter(l -> l.contains("{img:path(...)}")).findFirst().orElseThrow();
        assertThat(sectionLine, startsWith("pub/expressions.html|item.cover|'small'|"));
        assertThat(sectionLine, stringContainsInOrder("{#img} at line 1"));
        assertThat(nsLine, startsWith("pub/expressions.html|item.cover.or(item.fallback)|item.preset|"));
        assertThat(nsLine, stringContainsInOrder("{img:path(...)} at line 2"));
    }

    @Test
    public void testRemoteLiteralIsNotReported() throws IOException {
        assertThat(Files.readString(REPORT).contains("example.com"), is(false));
    }
}
