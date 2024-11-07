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
                "templates/pub/hello.txt")
                .addAsResource(new StringAsset(
                        "Index root!"),
                        "templates/pub/index.html")
                .addAsResource(new StringAsset(
                        "Index foo!"),
                        "templates/pub/foo/index.html")
                .addAsResource(new StringAsset(
                        "Index bar!"),
                        "templates/pub/bar/index.qute.html")
                .addAsResource(new StringAsset(
                        "Index bar sub!"),
                        "templates/pub/bar/sub.qute.html");
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
        given()
                .when().get("/foo/index.html")
                .then()
                .statusCode(200)
                .body(containsString("Index foo!"));
        given()
                .when().get("/bar/")
                .then()
                .statusCode(200)
                .body(containsString("Index bar!"));
        given()
                .when().get("/bar/index")
                .then()
                .statusCode(200)
                .body(containsString("Index bar!"));
        given()
                .when().get("/bar/index.qute.html")
                .then()
                .statusCode(200)
                .body(containsString("Index bar!"));
        given()
                .when().get("/bar/sub")
                .then()
                .statusCode(200)
                .body(containsString("Index bar sub!"));
        given()
                .when().get("/bar/sub.qute.html")
                .then()
                .statusCode(200)
                .body(containsString("Index bar sub!"));
    }
}
