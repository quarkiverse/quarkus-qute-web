package io.quarkiverse.qute.web.markdown.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.markdown.runtime.MarkdownSectionHelperFactory;
import io.quarkiverse.qute.web.markdown.runtime.commonmark.CommonMarkConverter;
import io.quarkus.qute.Engine;

public class QuarkusMarkdownTest {

    static MarkdownSectionHelperFactory sectionHelperFactory = new MarkdownSectionHelperFactory(new CommonMarkConverter());

    @Test
    public void testMd() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(sectionHelperFactory).build();
        assertEquals("<p>...</p>\n", engine.parse("{#md}...{/md}").render());
    }

    @Test
    public void testMarkdown() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(sectionHelperFactory).build();
        assertEquals("<p>...</p>\n", engine.parse("{#markdown}...{/markdown}").render());
    }

    @Test
    public void testH1() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(sectionHelperFactory).build();
        assertEquals("<h1>Quarkus and Roq</h1>\n", engine.parse("{#md}# Quarkus and Roq{/md}").render());
    }

    @Test
    void testInnerSections() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(sectionHelperFactory).build();

        String result = engine.parse("""
                <h1>Quarkus and Qute</h1>
                {#md}
                # Qute and Roq
                Here is a list:
                {#for item in items}
                - an {item} as a list item
                {/for}
                {/md}
                """).data("items", List.of("apple", "banana", "cherry"))
                .render();

        Assertions.assertEquals("""
                <h1>Quarkus and Qute</h1>
                <h1>Qute and Roq</h1>
                <p>Here is a list:</p>
                <ul>
                <li>an apple as a list item</li>
                <li>an banana as a list item</li>
                <li>an cherry as a list item</li>
                </ul>
                """, result);

    }
}
