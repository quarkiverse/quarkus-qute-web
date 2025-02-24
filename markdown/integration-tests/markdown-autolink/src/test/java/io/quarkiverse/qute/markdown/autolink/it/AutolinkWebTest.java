package io.quarkiverse.qute.markdown.autolink.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AutolinkWebTest {

    @Test
    void shouldGenerateAnchorElementCorrectly() {

        given().when()
                .get("/md")
                .then()
                .body(containsString("""
                        <p>Autolink extension: <a href="https://example.com">https://example.com</a></p>
                        """))
                .body(not(containsString(
                        """
                                <id="heading"
                                """)));
    }
}
