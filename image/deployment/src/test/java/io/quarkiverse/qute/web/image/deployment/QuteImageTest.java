package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.awt.Color;
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
import io.restassured.response.Response;

public class QuteImageTest {

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
    public void testDefaultAttributes() {
        Document doc = fetchAndParse("/images.html");
        Elements imgs = doc.select("img");
        assertThat("should have 6 img elements (directUrl tag does not produce <img>)", imgs, hasSize(6));

        Element defaultImg = imgs.get(0);
        assertThat(defaultImg.attr("loading"), is("lazy"));
        assertThat(defaultImg.attr("sizes"), is("auto"));
        assertThat(Integer.parseInt(defaultImg.attr("width")), greaterThan(0));
        assertThat(Integer.parseInt(defaultImg.attr("height")), greaterThan(0));
    }

    @Test
    public void testNoPictureWhenSingleFormatGenerated() {
        Document doc = fetchAndParse("/images.html");
        Elements pictures = doc.select("picture");
        assertThat("no picture elements when only one format is generated (webp skipped by ImageIO)",
                pictures.size(), is(0));

        Element defaultImg = doc.select("img").get(1);
        assertThat(defaultImg.attr("src"), containsString("/static/images/generated/"));
        assertThat("fallback src should use jpg", defaultImg.attr("src"), containsString(".jpg"));
        String srcset = defaultImg.attr("srcset");
        assertThat(srcset, containsString("640w"));
        assertThat(srcset, containsString("1024w"));
        assertThat(srcset, containsString("1920w"));
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
    public void testTagLevelAttributes() {
        Document doc = fetchAndParse("/images.html");
        Element withAttrs = doc.select("img").get(2);
        assertThat(withAttrs.attr("alt"), is("Small image"));
        assertThat(withAttrs.attr("class"), is("thumb"));
    }

    @Test
    public void testLoadingOverride() {
        Document doc = fetchAndParse("/images.html");
        Element eagerImg = doc.select("img").get(3);
        assertThat("loading should be overridden to eager", eagerImg.attr("loading"), is("eager"));
    }

    @Test
    public void testPixelRatioSrcset() {
        Document doc = fetchAndParse("/images.html");
        Element retinaImg = doc.select("img").get(4);
        String srcset = retinaImg.attr("srcset");
        assertThat("pixel ratio should use 1x descriptor", srcset, containsString("1x"));
        assertThat("pixel ratio should use 1.5x descriptor", srcset, containsString("1.5x"));
        assertThat("pixel ratio should use 2x descriptor", srcset, containsString("2x"));
        assertThat("pixel ratio should NOT use w descriptors", srcset, not(containsString("w")));
    }

    @Test
    public void testPixelRatioImageDimensions() throws IOException {
        Document doc = fetchAndParse("/images.html");
        Element retinaImg = doc.select("img").get(4);
        String srcset = retinaImg.attr("srcset");

        String url1x = extractUrlForDescriptor(srcset, "1x");
        String url2x = extractUrlForDescriptor(srcset, "2x");

        BufferedImage img1x = fetchImage(url1x);
        BufferedImage img2x = fetchImage(url2x);

        assertThat("1x image should be 160px wide", img1x.getWidth(), is(160));
        assertThat("2x image should be 320px wide", img2x.getWidth(), is(320));
    }

    @Test
    public void testCropDimensions() throws IOException {
        Document doc = fetchAndParse("/images.html");
        Element squareImg = doc.select("img").get(5);
        String src = squareImg.attr("src");

        BufferedImage cropped = fetchImage(src);
        assertThat("cropped image should be 200px wide", cropped.getWidth(), is(200));
        assertThat("cropped image should be square (200px tall)", cropped.getHeight(), is(200));
    }

    @Test
    public void testCropKeepsCenter() throws IOException {
        Document doc = fetchAndParse("/images.html");
        Element squareImg = doc.select("img").get(5);
        String src = squareImg.attr("src");

        BufferedImage cropped = fetchImage(src);
        Color center = new Color(cropped.getRGB(cropped.getWidth() / 2, cropped.getHeight() / 2));
        assertThat("center of cropped image should be green (from center stripe)",
                center.getGreen(), greaterThan(200));
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
    public void testResizedImageDimensions() throws IOException {
        Document doc = fetchAndParse("/images.html");
        Element img = doc.select("img").get(2);
        String srcset = img.attr("srcset");
        String url320 = extractUrlForWidth(srcset, "320w");

        BufferedImage generated = fetchImage(url320);
        assertThat("resized image width should be 320", generated.getWidth(), is(320));
        assertThat("resized image height should be proportional", generated.getHeight(), greaterThan(0));
    }

    @Test
    public void testImageRunTime() {
        RestAssured.given()
                .get("/rest")
                .then()
                .statusCode(200);
    }

    @Test
    public void testGlobPreProcessedImages() {
        Response resp = RestAssured.given()
                .get("/rest/dynamic?src=/static/images/white_1920_1080.png")
                .then()
                .statusCode(200)
                .extract().response();
        Document doc = Jsoup.parse(resp.body().asString());
        Element img = doc.select("img").first();
        assertThat("glob-processed image should have srcset", img.attr("srcset"), containsString("320w"));
        assertThat("glob-processed image should have srcset", img.attr("srcset"), containsString("640w"));
    }

    @Test
    public void testGlobOnlyImage() {
        // quadrants_400_200.png is template-scanned with 'retina' and 'square' presets,
        // but the glob processes all PNGs with 'small'. The dynamic endpoint uses 'small',
        // so this test verifies the glob-only processed entry is found at runtime.
        Response resp = RestAssured.given()
                .get("/rest/dynamic?src=/static/images/quadrants_400_200.png")
                .then()
                .statusCode(200)
                .extract().response();
        Document doc = Jsoup.parse(resp.body().asString());
        Element img = doc.select("img").first();
        String srcset = img.attr("srcset");
        assertThat("glob-only image should have 320w from small preset", srcset, containsString("320w"));
        assertThat("glob-only image should NOT have retina descriptors", srcset, not(containsString("x")));
    }

    @Test
    public void testDirectUrlPreset() {
        String body = RestAssured.given()
                .get("/images.html")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat("directUrl should output a raw URL path", body, containsString("/static/images/generated/"));
        assertThat("directUrl output should not be wrapped in <img>",
                body, not(containsString("preset=\"url-only\"")));
    }

    @Test
    public void testGracefulFallback() {
        Response resp = RestAssured.given()
                .get("/rest/dynamic?src=/unknown/image.jpg")
                .then()
                .statusCode(200)
                .extract().response();
        String body = resp.body().asString();
        assertThat("fallback should produce plain img tag", body, containsString("<img src=\"/unknown/image.jpg\""));
        assertThat("fallback should not have srcset", body, not(containsString("srcset")));
        assertThat("fallback should not have picture element", body, not(containsString("<picture")));
    }

    private Document fetchAndParse(String path) {
        String body = RestAssured.given()
                .get(path)
                .then()
                .statusCode(200)
                .extract().body().asString();
        return Jsoup.parse(body);
    }

    private String extractUrlForWidth(String srcset, String widthDescriptor) {
        for (String entry : srcset.split(",")) {
            entry = entry.trim();
            if (entry.endsWith(widthDescriptor)) {
                return entry.substring(0, entry.length() - widthDescriptor.length()).trim();
            }
        }
        return "";
    }

    private String extractUrlForDescriptor(String srcset, String descriptor) {
        for (String entry : srcset.split(",")) {
            entry = entry.trim();
            if (entry.endsWith(descriptor)) {
                return entry.substring(0, entry.length() - descriptor.length()).trim();
            }
        }
        return "";
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

        @Inject
        @Location("dynamic.html")
        Template dynamic;

        @GET
        public String get() {
            return index.instance().render();
        }

        @GET
        @Path("/dynamic")
        public String getDynamic(@jakarta.ws.rs.QueryParam("src") String src) {
            return dynamic.data("imageSrc", src).render();
        }
    }
}
