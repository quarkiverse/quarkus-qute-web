package io.quarkiverse.qute.web.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuteWebAsciiDocTest {

    @Test
    public void testTemplates() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .log().all()
                .body("html.body.p[0]", equalTo("Hello world!"))
                .body("html.body.div.div.div.ul.li[0].p", equalToCompressingWhiteSpace("Joe"))
                .body("html.body.div.h2", equalTo("Installation"))
                .body("html.body.div.div.div.div.pre.code.@class", equalTo("language-xml hljs"))
                .body(containsString(" &lt;groupId&gt;io.quarkiverse.qute.web&lt;/groupId&gt;"));

    }
}
