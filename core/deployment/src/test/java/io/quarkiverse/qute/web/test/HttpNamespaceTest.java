package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class HttpNamespaceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "{http:request.path} Hello {http:param('name','foo')}! {http:param('age')}"),
                "templates/pub/hello.txt")
                .addAsResource(new StringAsset(
                        "HX-Request={http:header('HX-Request')}::{http:headers.hx-request}"),
                        "templates/pub/headers.txt");
    });

    @Test
    public void testFailure() {
        given()
                .when()
                .get("/hello?age=42")
                .then()
                .statusCode(200)
                .body(containsString("/hello Hello foo! 42"));

        given()
                .when()
                .header("HX-Request", "true")
                .get("/headers")
                .then()
                .statusCode(200)
                .body(containsString("HX-Request=true::true"));
    }

}
