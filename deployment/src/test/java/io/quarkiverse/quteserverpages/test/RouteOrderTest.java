package io.quarkiverse.quteserverpages.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import javax.enterprise.event.Observes;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.ext.web.Router;

public class RouteOrderTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root
                .addAsResource(new StringAsset(
                        "BAZ!"),
                        "templates/baz.txt")
                .addAsResource(new StringAsset(
                        "BAR!"),
                        "templates/bar.txt")
                .addAsResource(new StringAsset(
                        "quarkus.qsp.route-order=5"),
                        "application.properties");
    });

    @Test
    public void testRouteOrder() {
        given()
                .when().get("/qsp/foo")
                .then()
                .statusCode(200)
                .body(containsString("Route FOO!"));

        given()
                .when().get("/qsp/baz")
                .then()
                .statusCode(200)
                .body(containsString("BAZ!"));

        given()
                .when().get("/qsp/bar")
                .then()
                .statusCode(200)
                .body(containsString("Route BAR!"));

    }

    public static class RouteInitializer {

        void addRoute(@Observes Router router) {
            router.route("/qsp/foo")
                    // this route should go after the qsp route
                    .order(10)
                    .handler(rc -> rc.response().setStatusCode(200).end("Route FOO!"));

            router.route("/qsp/bar")
                    // this route should go before the qsp route
                    .order(1)
                    .handler(rc -> rc.response().setStatusCode(200).end("Route BAR!"));
        }

    }

}
