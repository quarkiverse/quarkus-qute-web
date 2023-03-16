package io.quarkiverse.quteserverpages.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class CustomRootPathTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "Hello {name ?: 'world'}!"),
                "templates/hello.txt")
                .addAsResource(new StringAsset(
                        "quarkus.qsp.root-path=ping"),
                        "application.properties");
    });

    @Test
    public void testTemplates() {
        given()
                .when().get("/ping/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));

    }
}
