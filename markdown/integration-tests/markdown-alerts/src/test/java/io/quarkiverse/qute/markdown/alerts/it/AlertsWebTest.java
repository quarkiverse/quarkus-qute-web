package io.quarkiverse.qute.markdown.alerts.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AlertsWebTest {

    @Test
    void shouldRenderNoteAlert() {
        given().when()
                .get("/md")
                .then()
                .body(containsString("markdown-alert-note"));
    }

    @Test
    void shouldRenderTipAlert() {
        given().when()
                .get("/md")
                .then()
                .body(containsString("markdown-alert-tip"));
    }

    @Test
    void shouldRenderImportantAlert() {
        given().when()
                .get("/md")
                .then()
                .body(containsString("markdown-alert-important"));
    }

    @Test
    void shouldRenderWarningAlert() {
        given().when()
                .get("/md")
                .then()
                .body(containsString("markdown-alert-warning"));
    }

    @Test
    void shouldRenderCautionAlert() {
        given().when()
                .get("/md")
                .then()
                .body(containsString("markdown-alert-caution"));
    }

    @Test
    void shouldRenderCustomInfoAlert() {
        given().when()
                .get("/md")
                .then()
                .body(containsString("markdown-alert-info"))
                .body(containsString("Information"));
    }
}
