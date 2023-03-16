package io.quarkiverse.quteserverpages.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class QspTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "{#hello /} {name ?: 'world'}!"),
                "templates/hello.txt").addAsResource(
                        new StringAsset(
                                "Hello"),
                        "templates/tags/hello.txt");
    });

    @Test
    public void testTemplates() {
        given()
                .when().get("/qsp/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));

        given()
                .when().get("/qsp/tags/hello")
                .then()
                .statusCode(404);
    }
}
