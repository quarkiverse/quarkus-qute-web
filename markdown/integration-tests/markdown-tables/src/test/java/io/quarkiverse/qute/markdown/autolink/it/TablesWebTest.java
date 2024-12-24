package io.quarkiverse.qute.markdown.autolink.it;

import static io.restassured.RestAssured.given;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TablesWebTest {

    @Test
    void shouldGenerateHtmlTableCorrectly() {

        given().when()
                .get("/md")
                .then()
                .body(Matchers.containsString("<table>"))
                .body(Matchers.containsString("<tr>"))
                .body(Matchers.containsString("<th>First Header</th>"))
                .body(Matchers.containsString("<td>Content Cell</td>"));
    }
}
