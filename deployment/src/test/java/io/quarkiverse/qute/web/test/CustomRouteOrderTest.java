package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import jakarta.enterprise.event.Observes;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.ext.web.Router;

public class CustomRouteOrderTest {

    static int TEST_ORDER = 50_000;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root
                .addAsResource(new StringAsset(
                        "BAZ!"),
                        "templates/pub/baz.txt")
                .addAsResource(new StringAsset(
                        "BAR!"),
                        "templates/pub/bar.txt")
                .addAsResource(new StringAsset(
                        "PING!"),
                        "templates/pub/ping.txt")
                .addAsResource(new StringAsset(
                        "STATIC PING!"),
                        "META-INF/resources/ping.txt")
                .addAsResource(new StringAsset(
                        // This means that static resources should take precedence
                        "quarkus.qute.web.route-order=" + TEST_ORDER),
                        "application.properties");
    });

    @Test
    public void testRouteOrder() {
        given()
                .when().get("/foo")
                .then()
                .statusCode(200)
                .body(containsString("Route FOO!"));
        given()
                .when().get("/baz")
                .then()
                .statusCode(200)
                .body(containsString("BAZ!"));
        given()
                .when().get("/bar")
                .then()
                .statusCode(200)
                .body(containsString("Route BAR!"));
        given()
                .when().get("/ping.txt")
                .then()
                .statusCode(200)
                .body(containsString("STATIC PING!"));

    }

    public static class RouteInitializer {

        void addRoute(@Observes Router router) {
            router.route("/foo")
                    // this route should go after the qute-web route
                    .order(TEST_ORDER + 1)
                    .handler(rc -> rc.response().setStatusCode(200).end("Route FOO!"));

            router.route("/bar")
                    // this route should go before the qute-web route
                    .order(TEST_ORDER - 1)
                    .handler(rc -> rc.response().setStatusCode(200).end("Route BAR!"));
        }

    }

}
