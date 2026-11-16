package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * A literal source is found at render time wherever it is rendered from: an included layout, a user tag, or the
 * {@code img:tag} namespace method. Qute reports the page as the template of everything it includes, so the build
 * must key those literals by the page. The {@code retina} preset is used because the shared glob config covers the
 * {@code small} preset only, which would mask a miss.
 */
public class QuteImageLiteralRenderTest {

    static final String PHOTO = "/static/images/quadrants_400_200.png";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web/static")
                    .addAsResource(new StringAsset("{#img '" + PHOTO + "' preset='retina' data-test='layout'/}\n"
                            + "<div id=\"layout-path\">{img:path('" + PHOTO + "', 'retina')}</div>\n{#insert /}"),
                            "web/templates/layouts/shell.html")
                    .addAsResource(new StringAsset("{#img '" + PHOTO + "' preset='retina' data-test='tag'/}"),
                            "web/templates/tags/badge.html")
                    .addAsResource(new StringAsset("{#include layouts/shell}{#badge /}\n"
                            + "<div id=\"ns-tag\">{img:tag('" + PHOTO + "', 'retina')}</div>\n"
                            + "{#let p=img:path('" + PHOTO + "', 'retina')}<div id=\"let-path\">{p}</div>{/let}"
                            + "{/include}"),
                            "web/templates/pub/literals.html"));

    @Test
    public void testLiteralInIncludedLayout() {
        Document doc = page();
        assertThat(doc.selectFirst("img[data-test=layout]").attr("src"), containsString("/static/images/generated/"));
        assertThat(doc.getElementById("layout-path").text(), containsString("/static/images/generated/"));
    }

    @Test
    public void testLiteralInUserTag() {
        assertThat(page().selectFirst("img[data-test=tag]").attr("src"), containsString("/static/images/generated/"));
    }

    /** A section parameter is not a node of the template: the scan must look at the expressions, not the nodes. */
    @Test
    public void testLiteralInSectionParameter() {
        assertThat(page().getElementById("let-path").text(), containsString("/static/images/generated/"));
    }

    @Test
    public void testLiteralInNamespaceTag() {
        assertThat(page().select("#ns-tag img").first().attr("src"), containsString("/static/images/generated/"));
    }

    private static Document page() {
        return Jsoup.parse(RestAssured.get("/literals.html").then().statusCode(200).extract().asString());
    }
}
