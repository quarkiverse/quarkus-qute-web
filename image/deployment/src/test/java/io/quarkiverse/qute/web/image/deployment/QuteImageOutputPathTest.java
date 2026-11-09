package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class QuteImageOutputPathTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.output-path", "/img/gen")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"));

    @Test
    public void testGeneratedPathsUseConfiguredPrefix() {
        String body = RestAssured.get("/images.html").then().statusCode(200).extract().asString();
        Document doc = Jsoup.parse(body);
        String src = doc.select("img").get(1).attr("src");
        assertThat(src, startsWith("/img/gen/"));
        RestAssured.get(src).then().statusCode(200);
    }
}
