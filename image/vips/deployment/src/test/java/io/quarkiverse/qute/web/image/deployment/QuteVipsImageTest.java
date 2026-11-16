package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * What the vips converter adds over ImageIO: webp variants, hence pictures with several sources, and its own crop.
 * The tag itself is covered by the image module's tests.
 */
public class QuteVipsImageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"));

    @Test
    public void testPictureWithWebp() {
        Document doc = fetchAndParse("/images.html");
        Elements pictures = doc.select("picture");
        assertThat("default preset (webp+jpg) should produce picture elements", pictures.size(), greaterThan(0));
        Elements sources = pictures.get(0).select("source");
        assertThat("vips should produce multiple source elements (webp+jpg)", sources.size(), is(2));
        assertThat(sources.stream().anyMatch(s -> s.attr("type").contains("webp")), is(true));
        assertThat(sources.stream().anyMatch(s -> s.attr("type").contains("jpeg")), is(true));
        assertThat("img fallback src should use jpg", pictures.get(0).select("img").first().attr("src"),
                containsString(".jpg"));
    }

    @Test
    public void testCropDimensions() throws IOException {
        Element squareImg = fetchAndParse("/images.html").select("img").get(3);
        BufferedImage cropped = fetchImage(squareImg.attr("src"));
        assertThat("cropped image should be 200px wide", cropped.getWidth(), is(200));
        assertThat("cropped image should be square (200px tall)", cropped.getHeight(), is(200));
    }

    @Test
    public void testGeneratedImagesAccessible() {
        Document doc = fetchAndParse("/images.html");
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.contains("/static/images/generated/")) {
                RestAssured.given().basePath("").get(src).then().statusCode(200);
            }
        }
        for (Element source : doc.select("source")) {
            for (String entry : source.attr("srcset").split(",")) {
                String url = entry.trim().split("\\s")[0];
                if (url.contains("/static/images/generated/")) {
                    RestAssured.given().basePath("").get(url).then().statusCode(200);
                }
            }
        }
    }

    /**
     * vips can read SVG, but vectors pass through by design with every converter.
     */
    @Test
    public void testVectorSourcePassesThrough() {
        Document doc = fetchAndParse("/vector.html");
        Element img = doc.select("img").first();
        assertThat(img.attr("src"), is("/foo/bar/static/images/logo.svg"));
        assertThat(img.attr("srcset"), is(""));
        assertThat(doc.select("picture").size(), is(0));
    }

    /**
     * Variants carry none of the source's metadata: a remote host could plant arbitrary bytes in an XMP or EXIF
     * segment, and an author's own photo carries GPS coordinates. The fixture has an XMP segment with a marker.
     */
    @Test
    public void testVariantsCarryNoSourceMetadata() {
        String src = fetchAndParse("/metadata.html").select("img").first().attr("src");
        byte[] variant = RestAssured.given().basePath("").get(src).then().statusCode(200).extract().asByteArray();
        assertThat(src, containsString("/static/images/generated/"));
        assertThat(new String(variant, StandardCharsets.ISO_8859_1), not(containsString("QUTE_IMAGE_METADATA_MARKER")));
    }

    private Document fetchAndParse(String path) {
        return Jsoup.parse(RestAssured.given().get(path).then().statusCode(200).extract().body().asString());
    }

    private BufferedImage fetchImage(String url) throws IOException {
        try (InputStream is = RestAssured.given().basePath("").get(url).then().statusCode(200)
                .extract().body().asInputStream()) {
            return ImageIO.read(is);
        }
    }
}
