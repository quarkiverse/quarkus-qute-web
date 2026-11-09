package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

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

import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Images handed over by another extension through {@link QuteImageToProcessBuildItem} are converted and
 * registered under their declared path, so a template whose src evaluates to that path at render time finds them.
 */
public class QuteImageToProcessTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new ProduceStep())
                    .produces(ImagesDirBuildItem.class)
                    .produces(QuteImageToProcessBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("web"));

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
