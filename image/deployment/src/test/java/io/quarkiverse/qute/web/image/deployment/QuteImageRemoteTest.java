package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * A remote source is downloaded at build time, cached under the build dir with the extension of its real format,
 * and converted like a local file. A download failure is a warning: the url renders as is.
 */
public class QuteImageRemoteTest {

    static final Path BUILD_DIR = Path.of("target/qute-image-remote").toAbsolutePath();
    // the Unsplash shape: no extension in the url
    static final String PHOTO = FixtureServer.url("remote/photo");
    static final String MISSING = FixtureServer.url("remote/missing");
    // a png signature followed by garbage: downloads fine, cannot be read
    static final String CORRUPT = FixtureServer.url("remote/corrupt");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.remote.enabled", "true")
            .overrideConfigKey("quarkus.qute.image.verbose", "true")
            // the fixture server is plain http
            .overrideConfigKey("quarkus.qute.image.remote.allow-http", "true")
            .overrideConfigKey("quarkus.qute.image.build-dir", BUILD_DIR.toString())
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FixtureServer.class)
                    .addAsResource("web/static")
                    .addAsResource(new StringAsset("{#img '" + PHOTO + "' alt='Remote'/}\n"
                            + "<div id=\"p\">{img:path('" + PHOTO + "')}</div>"), "web/templates/pub/remote-ok.html")
                    .addAsResource(new StringAsset("{#img '" + MISSING + "' alt='Missing'/}"),
                            "web/templates/pub/remote-missing.html")
                    .addAsResource(new StringAsset("{#img '" + CORRUPT + "' alt='Corrupt'/}"),
                            "web/templates/pub/remote-corrupt.html"))
            .setLogRecordPredicate(record -> record.getLoggerName().startsWith("io.quarkiverse.qute.web.image"))
            .assertLogRecords(QuteImageRemoteTest::assertLogs);

    /** One download line for the photo, one warning each for the missing and the corrupt url. */
    static void assertLogs(List<LogRecord> records) {
        List<String> messages = records.stream().map(QuteImageLogTest::message).toList();
        assertTrue(records.stream().anyMatch(r -> QuteImageLogTest.isInfo(r)
                && QuteImageLogTest.message(r).startsWith("Downloaded photo.png (400x200, ")
                && QuteImageLogTest.message(r).contains(" KB) from " + PHOTO + " in ")), "no download line in " + messages);
        assertTrue(
                messages.stream().anyMatch(m -> m.startsWith("Remote image '" + MISSING + "' cannot be converted (HTTP 404)")),
                "no warning for the missing url in " + messages);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Remote image '" + CORRUPT + "' cannot be converted (")),
                "no warning for the corrupt download in " + messages);
    }

    @Test
    public void testRemoteSourceIsConverted() {
        Document doc = fetchAndParse("/remote-ok.html");
        Element img = doc.select("img").first();
        assertThat(img.attr("src"), containsString("/foo/bar/static/images/generated/"));
        assertThat("named after the url's last segment", img.attr("src"), containsString("photo"));
        assertThat(img.attr("srcset"), not(is("")));
        assertThat(doc.getElementById("p").text(), containsString("/static/images/generated/"));
    }

    @Test
    public void testOriginalIsCachedWithItsFormatExtension() throws IOException {
        try (Stream<Path> files = Files.list(BUILD_DIR.resolve("remote"))) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().endsWith(".png")),
                    "no cached png under " + BUILD_DIR.resolve("remote"));
        }
    }

    @Test
    public void testFailedDownloadRendersTheUrl() {
        Element img = fetchAndParse("/remote-missing.html").select("img").first();
        assertThat(img.attr("src"), is(MISSING));
        assertThat(img.attr("srcset"), is(""));
    }

    /** Bytes the converter cannot read are a warning like a failed download, not a build failure. */
    @Test
    public void testUnreadableDownloadRendersTheUrl() {
        Element img = fetchAndParse("/remote-corrupt.html").select("img").first();
        assertThat(img.attr("src"), is(CORRUPT));
        assertThat(img.attr("srcset"), is(""));
    }

    private static Document fetchAndParse(String path) {
        return Jsoup.parse(RestAssured.get(path).then().statusCode(200).extract().asString());
    }
}
