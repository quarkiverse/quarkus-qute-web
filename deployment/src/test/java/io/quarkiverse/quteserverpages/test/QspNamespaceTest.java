package io.quarkiverse.quteserverpages.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class QspNamespaceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "{qsp:request.path} Hello {qsp:param('name','foo')}! {qsp:param('age')}"),
                "templates/hello.txt");
    });

    @Test
    public void testFailure() {
        given()
                .when().get("/qsp/hello?age=42")
                .then()
                .statusCode(200)
                .body(containsString("/qsp/hello Hello foo! 42"));
    }

}
