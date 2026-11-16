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
                    .map(i -> String.join("|", i.templateId(), i.srcExpression(), i.preset(), i.origin()))
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
        assertThat(sectionLine, startsWith("pub/expressions.html|item.cover|small|"));
        assertThat(sectionLine, stringContainsInOrder("{#img} at line 1"));
        // the preset is always a literal: an expression there fails the build (QuteImageDynamicPresetTest)
        assertThat(nsLine, startsWith("pub/expressions.html|item.cover.or(item.fallback)|small|"));
        assertThat(nsLine, stringContainsInOrder("{img:path(...)} at line 2"));
    }

    @Test
    public void testIncludedAndTagUsagesAreReportedForTheIncludingTemplate() throws IOException {
        // pub/tree.html includes layouts/main (an {#img} usage) and calls {#card} (an img:path usage)
        List<String> lines = Files.readAllLines(REPORT).stream()
                .filter(l -> l.startsWith("pub/tree.html|")).toList();
        assertThat(lines, hasSize(2));
        assertThat(lines.get(0), startsWith("pub/tree.html|item.hero|small|"));
        assertThat(lines.get(0), stringContainsInOrder("{#img} at line 1 in layouts/main.html"));
        assertThat(lines.get(1), startsWith("pub/tree.html|item.thumb|small|"));
        assertThat(lines.get(1), stringContainsInOrder("{img:path(...)} at line 1 in tags/card.html"));
    }

    @Test
    public void testLayoutAndTagUsagesAreAlsoReportedForThemselves() throws IOException {
        List<String> lines = Files.readAllLines(REPORT);
        assertThat(lines.stream().filter(l -> l.startsWith("layouts/main.html|item.hero|")).count(), is(1L));
        assertThat(lines.stream().filter(l -> l.startsWith("tags/card.html|item.thumb|")).count(), is(1L));
    }

    @Test
    public void testRemoteLiteralIsNotReported() throws IOException {
        assertThat(Files.readString(REPORT).contains("example.com"), is(false));
    }
}
