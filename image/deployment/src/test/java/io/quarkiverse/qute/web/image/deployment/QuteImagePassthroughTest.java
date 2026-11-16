package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/** {@code quarkus.qute.image.passthrough} lists extensions used as is, matched regardless of case. */
public class QuteImagePassthroughTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.passthrough", "PNG")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"));

    @Test
    public void testConfiguredExtensionPassesThrough() {
        Element img = Jsoup.parse(RestAssured.get("/images.html").then().statusCode(200).extract().asString())
                .selectFirst("img[data-test=default]");
        assertThat(img.attr("src"), is("/foo/bar/static/images/white_1920_1080.png"));
        assertThat(img.attr("srcset"), is(""));
        assertThat(img.attr("width"), is(""));
    }
}
