package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class DefaultRouteOrderTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "BAZ!"),
                "templates/pub/baz.txt")
                // Static resource is hidden by the template
                .addAsResource(new StringAsset(
                        "STATIC BAZ!"),
                        "META-INF/resources/baz.txt");
    });

    @Test
    public void testRouteOrder() {
        given()
                .when().get("/baz.txt")
                .then()
                .statusCode(200)
                .body(containsString("BAZ!"));
    }

}
