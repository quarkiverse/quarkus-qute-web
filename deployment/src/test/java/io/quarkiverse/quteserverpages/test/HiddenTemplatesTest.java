package io.quarkiverse.quteserverpages.test;

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
                "templates/hello.txt")
                .addAsResource(new StringAsset(
                        "Bar {name ?: 'world'}!"),
                        "templates/foo/bar.txt")
                .addAsResource(new StringAsset(
                        "Baz {name ?: 'world'}!"),
                        "templates/foo/baz/baz.txt")
                .addAsResource(new StringAsset(
                        "quarkus.qsp.hidden-templates=hello.*|foo/baz/.*"),
                        "application.properties");
    });

    @Test
    public void testHiddenTemplates() {
        given()
                .when().get("/qsp/hello")
                .then()
                .statusCode(404);
        given()
                .when().get("/qsp/foo/baz/baz")
                .then()
                .statusCode(404);
        given()
                .when().get("/qsp/foo/bar")
                .then()
                .statusCode(200)
                .body(containsString("Bar world!"));

    }
}
