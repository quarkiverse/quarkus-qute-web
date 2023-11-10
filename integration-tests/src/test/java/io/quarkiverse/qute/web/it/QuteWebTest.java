package io.quarkiverse.qute.web.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuteWebTest {

    @Test
    public void testTemplates() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));

        // CDI bean
        given()
                .when().get("/names.html")
                .then()
                .statusCode(200)
                .body(containsString("<li>Violet</li>"));

        // Globals
        given()
                .when().get("/integers?name=foo")
                .then()
                .statusCode(200)
                .body(containsString("1:11:42"), containsString("Name: foo"));

        // Static method
        given()
                .when().get("/colors")
                .then()
                .statusCode(200)
                .body(containsString("red, green, blue"));

        // Enum
        given()
                .when().get("/nested/enum")
                .then()
                .statusCode(200)
                .body(is("OK"));

        // Not found
        given()
                .when().get("/fooooooo")
                .then()
                .statusCode(404);

        // Fragment
        given()
                .when().get("/fragment")
                .then()
                .statusCode(200)
                .body(containsString("<title>Fragment</title> "), containsString("<p>Foo!</p>"));
        given()
                .when().get("/fragment?frag=foo")
                .then()
                .statusCode(200)
                .body(is("<p>Foo!</p>"));
        given()
                .when().get("/fragment?frag=bar")
                .then()
                .statusCode(404);
    }
}
