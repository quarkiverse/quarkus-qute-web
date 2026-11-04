package io.quarkiverse.qute.web.image.dynamic.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;

import java.nio.file.Path;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;

import org.jboss.shrinkwrap.api.ShrinkWrap;
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
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class DynamicImageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(new Customiser())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("templates")
                    .addAsResource("images", "images"));

    public static class MyBuildStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            context.produce(ImagesDirBuildItem.of("images",
                    Path.of("target/test-classes/images")));
        }
    }

    public static class Customiser implements Consumer<BuildChainBuilder> {

        @Override
        public void accept(BuildChainBuilder buildChainBuilder) {
            buildChainBuilder.addBuildStep(new MyBuildStep())
                    .produces(ImagesDirBuildItem.class)
                    .build();
        }
    }

    @Test
    public void testDynamicResolution() {
        String body = RestAssured.given()
                .get("/rest/dynamic?src=/white_1920_1080.png")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Document doc = Jsoup.parse(body);
        Element img = doc.select("img").first();
        assertThat("dynamic image should have srcset", img.attr("srcset"), containsString("320w"));
        assertThat("dynamic image should have srcset", img.attr("srcset"), containsString("640w"));
        assertThat("dynamic image should have src", img.attr("src"), containsString("/static/images/generated/"));
    }

    @Test
    public void testDynamicImageAccessible() {
        String body = RestAssured.given()
                .get("/rest/dynamic?src=/white_1920_1080.png")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Document doc = Jsoup.parse(body);
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.contains("/static/images/generated/")) {
                RestAssured.given().get(src).then().statusCode(200);
            }
            String srcset = img.attr("srcset");
            if (!srcset.isBlank()) {
                for (String entry : srcset.split(",")) {
                    String url = entry.trim().split("\\s")[0];
                    if (url.contains("/static/images/generated/")) {
                        int statusCode = RestAssured.given().get(url).then()
                                .extract().statusCode();
                        assertThat("generated image should be accessible: " + url,
                                statusCode, org.hamcrest.Matchers.is(200));
                    }
                }
            }
        }
    }

    @Test
    public void testDynamicImageDimensions() {
        String body = RestAssured.given()
                .get("/rest/dynamic?src=/white_1920_1080.png")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Document doc = Jsoup.parse(body);
        Element img = doc.select("img").first();
        assertThat("should have width attribute", Integer.parseInt(img.attr("width")), greaterThan(0));
        assertThat("should have height attribute", Integer.parseInt(img.attr("height")), greaterThan(0));
    }

    @Test
    public void testFallbackForUnknownImage() {
        String body = RestAssured.given()
                .get("/rest/dynamic?src=/unknown/image.jpg")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat("fallback should produce plain img tag", body, containsString("<img src=\"/unknown/image.jpg\""));
        assertThat("fallback should not have srcset", body, not(containsString("srcset")));
    }

    @Test
    public void testCaching() {
        String body1 = RestAssured.given()
                .get("/rest/dynamic?src=/white_1920_1080.png")
                .then()
                .statusCode(200)
                .extract().body().asString();

        String body2 = RestAssured.given()
                .get("/rest/dynamic?src=/white_1920_1080.png")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat("cached result should match", body1, org.hamcrest.Matchers.is(body2));
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
