package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import jakarta.enterprise.event.Observes;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.ext.web.Router;

public class FailureTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addClass(ErrorHandler.class)
                .addAsResource(new StringAsset(
                        "Hello {name}!"),
                        "templates/pub/hello.txt");
    });

    @Test
    public void testFailure() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(500)
                .body(containsString("ERROR!!!"));
    }

    public static class ErrorHandler {

        void register(@Observes Router router) {
            router.route().failureHandler(rc -> rc.response().setStatusCode(500).end("ERROR!!!"));
        }

    }
}
