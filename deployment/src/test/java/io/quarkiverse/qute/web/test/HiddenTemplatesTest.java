package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class HiddenTemplatesTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "Hello {name ?: 'world'}!"),
                "templates/pub/hello.txt")
                .addAsResource(new StringAsset(
                        "Bar {name ?: 'world'}!"),
                        "templates/pub/foo/bar.txt")
                .addAsResource(new StringAsset(
                        "Baz {name ?: 'world'}!"),
                        "templates/pub/foo/baz/baz.txt")
                .addAsResource(new StringAsset(
                        "quarkus.qute.web.hidden-templates=hello.*|foo/baz/.*"),
                        "application.properties");
    });

    @Test
    public void testHiddenTemplates() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(404);
        given()
                .when().get("/foo/baz/baz")
                .then()
                .statusCode(404);
        given()
                .when().get("/foo/bar")
                .then()
                .statusCode(200)
                .body(containsString("Bar world!"));

    }
}
