package io.quarkiverse.qute.markdown.heading.it;

import static io.restassured.RestAssured.given;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HeadingAnchorWebTest {

    @Test
    void shouldGenerateAnchorElementCorrectly() {

        given().when()
                .get("/md")
                .then()
                .body(Matchers.containsString("""
                        <h3 id="this-is-a-level-3">This is a level 3</h3>
                        """));

    }
}
