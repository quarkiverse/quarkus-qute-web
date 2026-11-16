package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.QuteImageTemplateLocationBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * A template generated under target/ (no source set, no image next to the generated file) still resolves its
 * relative images when a {@link QuteImageTemplateLocationBuildItem} points at the real source: a file on disk, or
 * a {@code jar:} resource whose images next to it are loaded from the classpath.
 */
@SuppressWarnings("deprecation")
public class QuteImageTemplateLocationTest {

    static final String RESOURCE = "roq/index.html";
    static final Path SOURCE = Path.of("target/test-classes/" + RESOURCE).toAbsolutePath();
    static final Path GENERATED = Path.of("target/generated-roq").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new GeneratedTemplatesStep())
                    .produces(TemplatePathBuildItem.class)
                    .produces(GeneratedResourceBuildItem.class)
                    .produces(ImagesDirBuildItem.class)
                    .produces(QuteImageTemplateLocationBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("roq")
                    .addAsResource("web"));

    /** The same source generated twice: located by its file, and by a jar: URI as a packaged resource gives. */
    public static class GeneratedTemplatesStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                generate(context, "from-file.html", content);
                generate(context, "from-jar.html", content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(ImagesDirBuildItem.of("web"));
            context.produce(ImagesDirBuildItem.of("roq-public", Path.of("target/test-classes/roq-public")));
            context.produce(new QuteImageTemplateLocationBuildItem("from-file.html", SOURCE.toUri()));
            // the entry path is what matters, the jar does not have to exist
            context.produce(new QuteImageTemplateLocationBuildItem("from-jar.html",
                    URI.create("jar:file:/not/a/real/app.jar!/" + RESOURCE)));
        }

        private static void generate(BuildContext context, String path, String content) throws IOException {
            Path generated = GENERATED.resolve(path);
            Files.createDirectories(generated.getParent());
            Files.writeString(generated, content, StandardCharsets.UTF_8);
            context.produce(TemplatePathBuildItem.builder().path(path).fullPath(generated).content(content)
                    .extensionInfo("Roq").build());
            // a file-based template is not recorded: make it available at runtime like a host would
            context.produce(new GeneratedResourceBuildItem("templates/" + path,
                    content.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void testRelativeImageResolvesAgainstDeclaredFile() {
        assertRelativeImagesResolved("/rest/from-file");
    }

    @Test
    public void testRelativeImageResolvesFromClasspath() {
        assertRelativeImagesResolved("/rest/from-jar");
    }

    /** 'white_1920_1080.png' and 'Foé/Foé_1920_1080.png', relative to roq/index.html. */
    private static void assertRelativeImagesResolved(String path) {
        Document doc = Jsoup.parse(RestAssured.get(path).then().statusCode(200).extract().asString());
        assertThat(doc.selectFirst("img[data-test=relative]").attr("src"), containsString("/static/images/generated/"));
        assertThat(doc.selectFirst("img[data-test=relative-unicode]").attr("src"),
                containsString("/static/images/generated/"));
    }

    @jakarta.ws.rs.Path("/rest")
    public static class Endpoint {

        @Inject
        @Location("from-file.html")
        Template fromFile;

        @Inject
        @Location("from-jar.html")
        Template fromJar;

        @GET
        @jakarta.ws.rs.Path("/from-file")
        public String fromFile() {
            return fromFile.instance().render();
        }

        @GET
        @jakarta.ws.rs.Path("/from-jar")
        public String fromJar() {
            return fromJar.instance().render();
        }
    }
}
