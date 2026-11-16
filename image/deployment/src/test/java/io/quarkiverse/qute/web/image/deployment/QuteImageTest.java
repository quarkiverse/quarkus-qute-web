package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.imageio.ImageIO;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

/**
 * The {@code {#img}} tag end to end on the shared fixtures ({@code web/templates/pub/images.html} and the presets of
 * {@code application.properties}): each usage carries a {@code data-test} attribute it is selected by.
 */
public class QuteImageTest {

    // would convert if downloaded: proves remote.enabled=false (set in application.properties) never downloads
    static final String REMOTE = FixtureServer.url("remote/photo");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(new Customiser())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class, FixtureServer.class)
                    .addAsResource("roq")
                    .addAsResource("web")
                    .addAsResource(new StringAsset("{#img '" + REMOTE + "' alt='Remote'/}\n"
                            + "<div id=\"p\">{img:path('" + REMOTE + "')}</div>"), "web/templates/pub/remote-disabled.html"))
            .setLogRecordPredicate(record -> record.getLoggerName().startsWith("io.quarkiverse.qute.web.image"))
            .assertLogRecords(QuteImageTest::assertLogs);

    /** The documented warnings are logged, the default log is quiet, and nothing is downloaded when disabled. */
    static void assertLogs(List<LogRecord> records) {
        List<String> messages = records.stream().map(QuteImageLogTest::message).toList();
        assertTrue(records.stream().anyMatch(r -> r.getLevel().intValue() == Level.WARNING.intValue()
                && QuteImageLogTest.message(r)
                        .startsWith("Image '/static/images/photo.webp' cannot be read by the 'imageio' converter")),
                "no warning for the unreadable source in " + messages);
        assertFalse(QuteImageLogTest.info(records, "Converted "), "no per-source lines by default: " + messages);
        assertTrue(QuteImageLogTest.info(records, "Images: "), "no summary in " + messages);
        assertTrue(messages.stream().noneMatch(m -> m.startsWith("Downloaded ") || m.contains("cannot be converted")),
                "remote sources are disabled, nothing should be downloaded: " + messages);
    }

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
    public void testUserTagNamedImgDoesNotShadowTheExtension() {
        // web/templates/tags/img.html is a user tag named img, as a host's fallback would be: it is excluded
        assertThat(images().select("img[data-user-tag]"), hasSize(0));
    }

    @Test
    public void testDefaultAttributes() {
        Element img = img(images(), "default");
        assertThat(img.attr("loading"), is("lazy"));
        assertThat(img.attr("sizes"), is("auto"));
        assertThat(Integer.parseInt(img.attr("width")), greaterThan(0));
        assertThat(Integer.parseInt(img.attr("height")), greaterThan(0));
    }

    @Test
    public void testSingleFormatIsAnImgWithSrcset() {
        // the default preset asks for webp and jpg, ImageIO writes jpg only: one format, so no <picture>
        Element img = img(images(), "default");
        assertThat(img.parent().tagName(), not(is("picture")));
        assertThat(img.attr("src"), containsString("/static/images/generated/"));
        assertThat("fallback src should use jpg", img.attr("src"), containsString(".jpg"));
        assertThat(descriptors(img.attr("srcset")), is(List.of("640w", "1024w", "1920w")));
    }

    @Test
    public void testTwoFormatsMakeAPicture() {
        // the dual preset (png, jpg) is written in full by ImageIO: a <picture> with one <source> per format
        Element picture = images().selectFirst("picture.pic#par");
        assertThat("picture- and parent- attributes go to the <picture>", picture, notNullValue());
        assertThat(picture.select("source[type=image/png]"), hasSize(1));
        assertThat(picture.select("source[type=image/jpeg]"), hasSize(1));
        Element img = picture.selectFirst("img[data-test=dual]");
        assertThat("the img falls back to the preset's fallback format", img.attr("src"), containsString(".jpg"));
        assertThat(img.attr("srcset"), is(""));
    }

    @Test
    public void testCustomPreset() {
        assertThat(descriptors(img(images(), "small").attr("srcset")), is(List.of("320w", "640w")));
    }

    @Test
    public void testUrlsCarryTheRootPath() {
        // application.properties sets quarkus.http.root-path=/foo/bar: the variants are served under it
        Document doc = images();
        Element img = img(doc, "default");
        assertThat(img.attr("src"), startsWith("/foo/bar/static/images/generated/"));
        for (String candidate : img.attr("srcset").split(",")) {
            assertThat(candidate.trim(), startsWith("/foo/bar/static/images/generated/"));
        }
        assertThat(doc.getElementById("ns-url").text(), startsWith("/foo/bar/static/images/generated/"));
    }

    @Test
    public void testDimensionsFollowThePreset() {
        // the small preset (320, 640) on a 1920x1080 source shared with the default preset
        Element img = img(images(), "small");
        assertThat(img.attr("width"), is("640"));
        assertThat(img.attr("height"), is("360"));
    }

    @Test
    public void testVariantsStayWithinTheirPreset() {
        // quadrants_400_200.png is 320 wide in both the retina (no crop) and banner (4:1 crop) presets
        Document doc = images();
        Element retina = img(doc, "retina");
        assertThat(retina.attr("width"), is("320"));
        assertThat(retina.attr("height"), is("160"));
        Element banner = img(doc, "banner");
        assertThat(banner.attr("width"), is("320"));
        assertThat(banner.attr("height"), is("80"));
        // white_1920_1080.png is 640 wide in both the small (quality 70) and default presets: one entry each
        assertThat(descriptors(img(doc, "small").attr("srcset")), is(List.of("320w", "640w")));
    }

    @Test
    public void testWidthLargerThanTheSourceIsClampedOnce() {
        // quadrants_400_200.png is 400 wide: the small preset (320, 640) gives 320 and 400, not 640
        Element img = dynamic("/static/images/quadrants_400_200.png");
        assertThat(descriptors(img.attr("srcset")), is(List.of("320w", "400w")));
        assertThat(img.attr("src"), containsString("-400-"));
        assertThat(img.attr("width"), is("400"));
    }

    @Test
    public void testTagLevelAttributes() {
        Element img = img(images(), "small");
        assertThat(img.attr("alt"), is("Small image"));
        assertThat(img.attr("class"), is("thumb"));
    }

    @Test
    public void testLoadingOverride() {
        assertThat(img(images(), "eager").attr("loading"), is("eager"));
    }

    @Test
    public void testPixelRatioSrcset() {
        assertThat(descriptors(img(images(), "retina").attr("srcset")), is(List.of("1x", "1.5x", "2x")));
    }

    @Test
    public void testPixelRatioImageDimensions() throws IOException {
        String srcset = img(images(), "retina").attr("srcset");
        assertThat(fetchImage(urlFor(srcset, "1x")).getWidth(), is(160));
        assertThat(fetchImage(urlFor(srcset, "2x")).getWidth(), is(320));
    }

    @Test
    public void testCropDimensions() throws IOException {
        BufferedImage cropped = fetchImage(img(images(), "square").attr("src"));
        assertThat(cropped.getWidth(), is(200));
        assertThat("cropped image should be square", cropped.getHeight(), is(200));
    }

    @Test
    public void testCropKeepsCenter() throws IOException {
        BufferedImage cropped = fetchImage(img(images(), "square").attr("src"));
        Color center = new Color(cropped.getRGB(cropped.getWidth() / 2, cropped.getHeight() / 2));
        assertThat("center of cropped image should be green (from center stripe)", center.getGreen(), greaterThan(200));
    }

    @Test
    public void testGeneratedImagesAccessible() {
        Document doc = images();
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.contains("/static/images/generated/")) {
                // rendered urls carry the root path already
                RestAssured.given().basePath("").get(src).then().statusCode(200);
            }
        }
        for (Element source : doc.select("source")) {
            for (String entry : source.attr("srcset").split(",")) {
                RestAssured.given().basePath("").get(entry.trim().split("\\s")[0]).then().statusCode(200);
            }
        }
    }

    @Test
    public void testResizedImageDimensions() throws IOException {
        BufferedImage generated = fetchImage(urlFor(img(images(), "small").attr("srcset"), "320w"));
        assertThat(generated.getWidth(), is(320));
        assertThat(generated.getHeight(), is(180));
    }

    /** Relative sources next to the template, under its _images/, or prefixed with an underscore. */
    @Test
    public void testRelativeSources() {
        Document doc = images();
        for (String test : List.of("relative", "under-images", "underscored")) {
            assertThat(test, img(doc, test).attr("src"), containsString("/foo/bar/static/images/generated/"));
        }
    }

    /** roq/index.html, a template handed over by a host with its source location: NFC and NFD names, a local dir. */
    @Test
    public void testHostTemplate() {
        Document doc = Jsoup.parse(RestAssured.get("/rest").then().statusCode(200).extract().asString());
        for (String test : List.of("relative", "absolute", "absolute-unicode", "relative-unicode", "local-dir")) {
            assertThat(test, img(doc, test).attr("src"), startsWith("/foo/bar/static/images/generated/"));
        }
    }

    @Test
    public void testGlobProcessedImages() {
        // the glob processes every PNG with the small preset: a template-scanned image gets it on top of its own
        // presets, and an image no template declares with that preset is found at runtime under it
        for (String src : List.of("/static/images/white_1920_1080.png", "/static/images/quadrants_400_200.png")) {
            List<String> descriptors = descriptors(dynamic(src).attr("srcset"));
            assertThat(src, descriptors.get(0), is("320w"));
            for (String descriptor : descriptors) {
                assertThat("the small preset has no pixel ratio descriptors", descriptor, matchesPattern("\\d+w"));
            }
        }
    }

    @Test
    public void testDirectUrlPreset() {
        Element direct = images().getElementById("direct");
        assertThat(direct.select("img"), hasSize(0));
        assertThat(direct.text().trim(),
                matchesPattern("/foo/bar/static/images/generated/[0-9a-f]{8}/white-1920-1080-640-[0-9a-f]+\\.jpg"));
    }

    @Test
    public void testNamespacePath() {
        String url = images().getElementById("ns-url").text().trim();
        assertThat(url, containsString("/static/images/generated/"));
        assertThat(url, containsString(".jpg"));
    }

    @Test
    public void testNamespaceTag() {
        Element img = images().select("#ns-tag img").first();
        assertThat(img.attr("src"), containsString("/static/images/generated/"));
        assertThat(descriptors(img.attr("srcset")), is(List.of("320w", "640w")));
    }

    @Test
    public void testRemoteLiteralPassesThroughWhenDisabled() {
        Document doc = fetchAndParse("/remote-disabled.html");
        Element img = doc.select("img").first();
        assertThat(img.attr("src"), is(REMOTE));
        assertThat(img.attr("srcset"), is(""));
        assertThat(doc.getElementById("p").text(), is(REMOTE));
    }

    @Test
    public void testVectorSourcePassesThrough() {
        Document doc = fetchAndParse("/vector.html");
        Element img = doc.select("img").first();
        assertThat(img.attr("src"), is("/foo/bar/static/images/logo.svg"));
        assertThat(img.attr("srcset"), is(""));
        assertThat(img.attr("width"), is(""));
        assertThat(doc.select("picture"), hasSize(0));
        assertThat(doc.getElementById("p").text(), is("/foo/bar/static/images/logo.svg"));
    }

    /** ImageIO has no WebP reader: the source cannot be read and passes through (the warning is asserted in the logs). */
    @Test
    public void testUnreadableSourcePassesThrough() {
        Document doc = fetchAndParse("/webp.html");
        Element img = doc.select("img").first();
        assertThat(img.attr("src"), is("/foo/bar/static/images/photo.webp"));
        assertThat(img.attr("srcset"), is(""));
        assertThat(img.attr("width"), is(""));
        assertThat(doc.getElementById("p").text(), is("/foo/bar/static/images/photo.webp"));
    }

    /** An animated gif has more than one frame: converting it would keep the first one only, so it passes through. */
    @Test
    public void testAnimatedGifPassesThrough() {
        Element img = img(images(), "animated");
        assertThat(img.attr("src"), is("/foo/bar/static/images/animated.gif"));
        assertThat(img.attr("srcset"), is(""));
    }

    @Test
    public void testMarkupHasNoStraySpaces() {
        String body = RestAssured.get("/images.html").then().statusCode(200).extract().asString();
        assertThat(body, not(matchesPattern("(?s).*<(img|picture|source)[^>]* >.*")));
    }

    @Test
    public void testNullSourceRendersNothing() {
        String body = RestAssured.get("/rest/dynamic").then().statusCode(200).extract().asString();
        assertThat(body, not(containsString("<img")));
    }

    @Test
    public void testGracefulFallback() {
        Element img = dynamic("/unknown/image.jpg");
        assertThat(img.attr("src"), is("/unknown/image.jpg"));
        assertThat(img.attr("srcset"), is(""));
        assertThat(img.parent().tagName(), not(is("picture")));
    }

    // --- helpers

    private static Document images() {
        return fetchAndParse("/images.html");
    }

    private static Element img(Document doc, String test) {
        Element img = doc.selectFirst("img[data-test=" + test + "]");
        assertThat("no img with data-test=" + test, img, notNullValue());
        return img;
    }

    /** The img rendered by dynamic.html for a source (the small preset). */
    private static Element dynamic(String src) {
        return Jsoup.parse(RestAssured.get("/rest/dynamic?src=" + src).then().statusCode(200).extract().asString())
                .selectFirst("img");
    }

    private static Document fetchAndParse(String path) {
        return Jsoup.parse(RestAssured.get(path).then().statusCode(200).extract().asString());
    }

    /** The descriptors of a srcset, in order: {@code 320w}, {@code 2x}... */
    static List<String> descriptors(String srcset) {
        return srcset.isBlank() ? List.of()
                : List.of(srcset.split(",")).stream().map(entry -> entry.trim().split("\\s")[1]).toList();
    }

    /** The url of the srcset entry with the given descriptor. */
    private static String urlFor(String srcset, String descriptor) {
        for (String entry : srcset.split(",")) {
            String[] parts = entry.trim().split("\\s");
            if (parts[1].equals(descriptor)) {
                return parts[0];
            }
        }
        throw new AssertionError("no " + descriptor + " in " + srcset);
    }

    private static BufferedImage fetchImage(String url) throws IOException {
        try (InputStream is = RestAssured.given().basePath("").get(url).then().statusCode(200)
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
