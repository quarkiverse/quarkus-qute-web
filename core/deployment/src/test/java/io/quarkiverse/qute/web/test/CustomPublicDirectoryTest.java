package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class CustomPublicDirectoryTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "Hello {name ?: 'world'}!"),
                "templates/pub/hello.txt")
                .addAsResource(new StringAsset(
                        "Bar {name ?: 'world'}!"),
                        "templates/ping/pong/bar.txt")
                .addAsResource(new StringAsset(
                        "quarkus.qute.web.public-dir=ping/pong"),
                        "application.properties");
    });

    @Test
    public void testCustomPublicDir() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(404);
        given()
                .when().get("/bar")
                .then()
                .statusCode(200)
                .body(containsString("Bar world!"));

    }
}
