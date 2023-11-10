package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class DefaultRootPathTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "Hello {name ?: 'world'}!"),
                "templates/web/hello.txt")
                .addAsResource(new StringAsset(
                        "Index root!"),
                        "templates/web/index.html")
                .addAsResource(new StringAsset(
                        "Index foo!"),
                        "templates/web/foo/index.html");
    });

    @Test
    public void testTemplates() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Index root!"));
        given()
                .when().get("/foo/")
                .then()
                .statusCode(200)
                .body(containsString("Index foo!"));
        given()
                .when().get("/foo/index")
                .then()
                .statusCode(200)
                .body(containsString("Index foo!"));
    }
}
