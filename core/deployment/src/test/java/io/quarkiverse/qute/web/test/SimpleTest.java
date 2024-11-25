package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class SimpleTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset("{#hello /} {name ?: 'world'}!"), "templates/pub/hello.txt")
                .addAsResource(new StringAsset("Hello"), "templates/tags/hello.txt");
    });

    @Test
    public void testTemplates() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));

        given()
                .when().get("/tags/hello")
                .then()
                .statusCode(404);
    }
}
