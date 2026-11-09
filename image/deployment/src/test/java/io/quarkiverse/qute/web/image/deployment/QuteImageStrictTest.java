package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * With {@code quarkus.qute.image.strict=true} a source that was not processed at build time is an error
 * instead of a plain {@code <img>} fallback; remote URLs and blank sources are not lookups and stay unaffected.
 */
public class QuteImageStrictTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.strict", "true")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("web"));

    @Test
    public void testMissFailsRendering() {
        RestAssured.get("/rest/dynamic?src=/unknown/image.jpg").then().statusCode(500);
    }

    @Test
    public void testRemoteStillPassesThrough() {
        String body = RestAssured.get("/rest/dynamic?src=https://example.com/a.jpg").then().statusCode(200)
                .extract().asString();
        assertThat(body, containsString("<img src=\"https://example.com/a.jpg\""));
    }

    @Test
    public void testHitStillRenders() {
        RestAssured.get("/images.html").then().statusCode(200);
    }

    @Test
    public void testBlankSrcRendersNothing() {
        String body = RestAssured.get("/rest/dynamic?src=").then().statusCode(200).extract().asString();
        assertThat(body, not(containsString("<img")));
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
