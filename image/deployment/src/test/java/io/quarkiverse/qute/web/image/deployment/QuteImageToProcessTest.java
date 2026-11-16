package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageProcessedBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Images handed over by another extension through {@link QuteImageToProcessBuildItem} are converted and
 * registered under their declared path, so a template whose src evaluates to that path at render time finds them.
 */
public class QuteImageToProcessTest {

    static final Path REPORT = Path.of("target/qute-image-processed.txt");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> {
                builder.addBuildStep(new ProduceStep())
                        .produces(ImagesDirBuildItem.class)
                        .produces(QuteImageToProcessBuildItem.class)
                        .build();
                builder.addBuildStep(new ReportStep())
                        .consumes(QuteImageProcessedBuildItem.class)
                        .produces(GeneratedResourceBuildItem.class)
                        .build();
            })
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("web"));

    /** Writes one line per processed usage: {@code templateId|declaredPath|passThrough|variants}. */
    public static class ReportStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            String report = context.consume(QuteImageProcessedBuildItem.class).tags().stream()
                    .map(t -> String.join("|", String.valueOf(t.templateId()), t.declaredPath(),
                            String.valueOf(t.image().passThrough()), String.valueOf(t.image().generated().size())))
                    .sorted()
                    .collect(Collectors.joining("\n"));
            try {
                Files.writeString(REPORT, report, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(new GeneratedResourceBuildItem("META-INF/qute-image-processed-test.txt",
                    report.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void testProcessedImagesAreReported() throws IOException {
        List<String> lines = Files.readAllLines(REPORT);
        // a host item is not bound to a template
        assertThat(lines, hasItem(startsWith("null|/virtual/hero.png|false|2")));
        assertThat(lines, hasItem(startsWith("null|/virtual/bytes.png|false|2")));
        // a literal usage is reported under its template
        assertThat(lines, hasItem(startsWith("pub/images.html|/static/images/white_1920_1080.png|false|")));
        // a passthrough source has no variants
        assertThat(lines, hasItem(startsWith("pub/vector.html|/static/images/logo.svg|true|0")));
    }

    public static class ProduceStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            context.produce(ImagesDirBuildItem.of("web"));
            context.produce(QuteImageToProcessBuildItem.of("/virtual/hero.png", "small",
                    java.nio.file.Path.of("target/test-classes/roq-public/Foé_1920_1081.png")));
            byte[] bytes;
            try (InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("web/static/images/white_1920_1080.png")) {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(QuteImageToProcessBuildItem.of("/virtual/bytes.png", "small", "bytes.png", bytes));
        }
    }

    @Test
    public void testFileSourceIsProcessed() {
        Element img = firstImg("/virtual/hero.png");
        String srcset = img.attr("srcset");
        assertThat(srcset, containsString("320w"));
        assertThat(srcset, containsString("640w"));
        assertThat(img.attr("src"), containsString("/static/images/generated/"));
    }

    @Test
    public void testBytesSourceIsProcessed() {
        Element img = firstImg("/virtual/bytes.png");
        String srcset = img.attr("srcset");
        assertThat(srcset, containsString("320w"));
        assertThat(srcset, containsString("640w"));
        assertThat(img.attr("src"), containsString("/static/images/generated/"));
    }

    private static Element firstImg(String src) {
        String body = RestAssured.get("/rest/dynamic?src=" + src).then().statusCode(200).extract().asString();
        Document doc = Jsoup.parse(body);
        return doc.select("img").first();
    }

    @jakarta.ws.rs.Path("/rest")
    public static class Endpoint {

        @Inject
        @Location("dynamic.html")
        Template dynamic;

        @GET
        @jakarta.ws.rs.Path("/dynamic")
        public String getDynamic(@QueryParam("src") String src) {
            return dynamic.data("imageSrc", src).render();
        }
    }
}
