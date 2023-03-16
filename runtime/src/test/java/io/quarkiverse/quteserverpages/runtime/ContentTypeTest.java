package io.quarkiverse.quteserverpages.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ContentTypeTest {

    @Test
    public void testContentType() {
        assertContentType("text/html", "text", "html");
        assertContentType("text/*", "text", "*");
        assertContentType("application/xml;q=0.9", "application", "xml");
        assertContentType("*/*;q=0.8", "*", "*");
        assertTrue(new ContentType("text/html").matches("text", "html"));
        assertTrue(new ContentType("text/html").matches("*", "html"));
        assertTrue(new ContentType("text/*").matches("text", "html"));
        assertTrue(new ContentType("*/*").matches("text", "html"));
        assertTrue(new ContentType("text/html").matches("*", "*"));
    }

    private void assertContentType(String value, String type, String subtype) {
        ContentType contentType = new ContentType(value);
        assertEquals(type, contentType.type);
        assertEquals(subtype, contentType.subtype);
    }

}
