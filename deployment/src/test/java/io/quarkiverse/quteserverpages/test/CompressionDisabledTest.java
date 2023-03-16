package io.quarkiverse.quteserverpages.test;

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class CompressionDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root
                    .addAsResource(new StringAsset("{cdi:vertxRequest.getParam('id')}"), "templates/file.txt")
                    .addAsResource(new StringAsset("{cdi:vertxRequest.getParam('id')}"), "templates/document.foo"))
            .overrideConfigKey("quarkus.http.enable-compression", "false")
            .overrideConfigKey("quarkus.qute.suffixes", "txt,foo");

    @Test
    public void testPages() {
        assertUncompressed("/qsp/file?id=1", "1");
        assertUncompressed("/qsp/document.foo?id=2", "2");
    }

    private void assertUncompressed(String path, String body) {
        ExtractableResponse<Response> response = get(path)
                .then().statusCode(200).extract();
        assertTrue(response.header("Content-Encoding") == null, response.headers().toString());
        assertEquals(body, response.asString());
    }

}
