package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class QuteVipsImageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(new Customiser())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("roq")
                    .addAsResource("web"));

    public static class MyBuildStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            try {
                byte[] bytes = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("/roq/index.html").readAllBytes();
                context.produce(TemplatePathBuildItem.builder()
                        .path("index.html")
                        .source(URI.create("target/test-classes/roq/index.html"))
                        .content(new String(bytes, StandardCharsets.UTF_8))
                        .extensionInfo("Roq")
                        .build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            context.produce(ImagesDirBuildItem.of("web"));
            context.produce(
                    ImagesDirBuildItem.of("roq-public", java.nio.file.Path.of("target/test-classes/roq-public")));
            context.produce(
                    ImagesDirBuildItem.of("", java.nio.file.Path.of("target/test-classes/roq-root")));
        }
    }

    public static class Customiser implements Consumer<BuildChainBuilder> {

        @Override
        public void accept(BuildChainBuilder buildChainBuilder) {
            buildChainBuilder.addBuildStep(new MyBuildStep())
                    .produces(TemplatePathBuildItem.class)
                    .produces(ImagesDirBuildItem.class)
                    .build();
        }
    }

    @Test
    public void testPictureWithWebp() {
        Document doc = fetchAndParse("/images.html");
        Elements pictures = doc.select("picture");
        assertThat("default preset (webp+jpg) should produce picture elements", pictures.size(), greaterThan(0));

        Element picture = pictures.get(0);
        Elements sources = picture.select("source");
        assertThat("vips should produce multiple source elements (webp+jpg)", sources.size(), is(2));

        boolean hasWebp = sources.stream().anyMatch(s -> s.attr("type").contains("webp"));
        boolean hasJpeg = sources.stream().anyMatch(s -> s.attr("type").contains("jpeg"));
        assertThat("should have webp source", hasWebp, is(true));
        assertThat("should have jpeg source", hasJpeg, is(true));

        Element img = picture.select("img").first();
        assertThat("img fallback src should use jpg", img.attr("src"), containsString(".jpg"));
    }

    @Test
    public void testCustomPreset() {
        Document doc = fetchAndParse("/images.html");
        Element smallImg = doc.select("img").get(2);
        String srcset = smallImg.attr("srcset");
        assertThat("small preset should have 320w", srcset, containsString("320w"));
        assertThat("small preset should have 640w", srcset, containsString("640w"));
        assertThat("small preset should NOT have 1024w", srcset, not(containsString("1024w")));
    }

    @Test
    public void testCropDimensions() throws IOException {
        Document doc = fetchAndParse("/images.html");
        Element squareImg = doc.select("img").get(3);
        String src = squareImg.attr("src");

        BufferedImage cropped = fetchImage(src);
        assertThat("cropped image should be 200px wide", cropped.getWidth(), is(200));
        assertThat("cropped image should be square (200px tall)", cropped.getHeight(), is(200));
    }

    @Test
    public void testGeneratedImagesAccessible() {
        Document doc = fetchAndParse("/images.html");
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.contains("/static/images/generated/")) {
                RestAssured.given().get(src).then().statusCode(200);
            }
        }
        for (Element source : doc.select("source")) {
            for (String entry : source.attr("srcset").split(",")) {
                String url = entry.trim().split("\\s")[0];
                if (url.contains("/static/images/generated/")) {
                    RestAssured.given().get(url).then().statusCode(200);
                }
            }
        }
    }

    @Test
    public void testRoqTemplateRenders() {
        RestAssured.given()
                .get("/rest")
                .then()
                .statusCode(200);
    }

    private Document fetchAndParse(String path) {
        String body = RestAssured.given()
                .get(path)
                .then()
                .statusCode(200)
                .extract().body().asString();
        return Jsoup.parse(body);
    }

    private BufferedImage fetchImage(String url) throws IOException {
        try (InputStream is = RestAssured.given().get(url).then().statusCode(200)
                .extract().body().asInputStream()) {
            return ImageIO.read(is);
        }
    }

    @Path("/rest")
    public static class Endpoint {
        @Inject
        @Location("index.html")
        Template index;

        @GET
        public String get() {
            return index.instance().render();
        }
    }
}
