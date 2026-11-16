package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * A plain application (no host registering image dirs): images under {@code META-INF/resources}, Quarkus's own
 * static root, resolve by their url. Dev mode follows the sources: a replaced image gets new variants, and a usage
 * added to a template is converted.
 */
public class QuteImageDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web/static/images/quadrants_400_200.png", "META-INF/resources/images/photo.png")
                    .addAsResource("web/static/images/white_1920_1080.png", "META-INF/resources/images/other.png")
                    .addAsResource(new StringAsset("quarkus.qute.image.presets.small.formats=jpg\n"
                            + "quarkus.qute.image.presets.small.widths=320\n"
                            + "quarkus.qute.image.remote.enabled=false\n"), "application.properties")
                    .addAsResource(new StringAsset("{#img '/images/photo.png' preset='small' data-test='photo'/}\n"),
                            "web/templates/pub/dev.html"));

    @Test
    public void testReplacedSourceGetsNewVariants() throws IOException {
        String before = img("photo").attr("src");
        assertThat(before, containsString("/static/images/generated/"));

        // the same name, other bytes: the digest in the url must change
        byte[] other = Files.readAllBytes(Path.of("target/test-classes/web/static/images/white_1920_1080.png"));
        test.addResourceFile("META-INF/resources/images/photo.png", other);

        String after = img("photo").attr("src");
        assertThat(after, containsString("/static/images/generated/"));
        assertThat("the variants should follow the new bytes", after, not(is(before)));
    }

    @Test
    public void testUsageAddedToATemplateIsConverted() {
        test.modifyResourceFile("web/templates/pub/dev.html",
                s -> s + "{#img '/images/other.png' preset='small' data-test='other'/}\n");
        assertThat(img("other").attr("src"), containsString("/static/images/generated/"));
    }

    private static Element img(String dataTest) {
        return Jsoup.parse(RestAssured.get("/dev.html").then().statusCode(200).extract().asString())
                .selectFirst("img[data-test=" + dataTest + "]");
    }
}
