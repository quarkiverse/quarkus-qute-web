package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.DataInitializer;
import io.quarkus.test.QuarkusUnitTest;

public class DataInitializerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClass(SimpleInitializer.class)
                    .addAsResource(new StringAsset(
                            "Hello {name ?: 'foo'}!"),
                            "templates/pub/baz.txt")
                    .addAsResource(new StringAsset(
                            "Hello {name ?: 'foo'}!"),
                            "templates/pub/hello.txt"));

    @Test
    public void testInit() {
        given()
                .when().get("/baz")
                .then()
                .statusCode(200)
                .body(containsString("Hello there!"));
        given()
                .when().get("/hello?name=Andy")
                .then()
                .statusCode(200)
                .body(containsString("Hello Andy!"));
    }

    @Singleton
    public static class SimpleInitializer implements DataInitializer {

        @ConfigProperty(name = "your.name", defaultValue = "there")
        String name;

        @Override
        public void initialize(InitContext ctx) {
            if (ctx.path().endsWith("hello")) {
                ctx.templateInstance().data("name", ctx.request().getParam("name"));
            } else {
                ctx.templateInstance().data("name", name);
            }

        }

    }
}
