package io.quarkiverse.quteserverpages.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QspTest {

    @Test
    public void testTemplates() {
        given()
                .when().get("/qsp/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));

        // CDI bean
        given()
                .when().get("/qsp/names.html")
                .then()
                .statusCode(200)
                .body(containsString("<li>Violet</li>"));

        // Globals
        given()
                .when().get("/qsp/integers?name=foo")
                .then()
                .statusCode(200)
                .body(containsString("1:11:42"), containsString("Name: foo"));

        // Static method
        given()
                .when().get("/qsp/colors")
                .then()
                .statusCode(200)
                .body(containsString("red, green, blue"));

        // Enum
        given()
                .when().get("/qsp/nested/enum")
                .then()
                .statusCode(200)
                .body(is("OK"));

        // Not found
        given()
                .when().get("/qsp/fooooooo")
                .then()
                .statusCode(404);

        // Fragment
        given()
                .when().get("/qsp/fragment")
                .then()
                .statusCode(200)
                .body(containsString("<title>Fragment</title> "), containsString("<p>Foo!</p>"));
        given()
                .when().get("/qsp/fragment?frag=foo")
                .then()
                .statusCode(200)
                .body(is("<p>Foo!</p>"));
        given()
                .when().get("/qsp/fragment?frag=bar")
                .then()
                .statusCode(404);
    }
}
