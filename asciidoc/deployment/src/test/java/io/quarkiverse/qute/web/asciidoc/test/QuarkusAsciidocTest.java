package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocSectionHelperFactory;
import io.quarkus.qute.Engine;

public class QuarkusAsciidocTest {

    @Test
    public void shouldConvertUsingAsciiTag() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(new AsciidocSectionHelperFactory()).build();

        String result = engine.parse("{#ascii}...{/ascii}").render();

        assertThat(result).contains("""
                 <p>
                ...
                 </p>
                """);
    }

    @Test
    public void shouldConvertUsingAsciidocTag() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(new AsciidocSectionHelperFactory()).build();

        String result = engine.parse("{#asciidoc}...{/asciidoc}").render();

        assertThat(result).contains("""
                 <p>
                ...
                 </p>
                """);
    }

    @Test
    public void testH1() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(new AsciidocSectionHelperFactory()).build();

        String result = engine.parse("{#ascii}= Quarkus and Roq{/ascii}").render();

        assertThat(result).contains("<h1>Quarkus and Roq</h1>");
    }

    @Test
    void shouldConvertWithForTagInsideAsciiTag() {

        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(new AsciidocSectionHelperFactory()).build();

        String result = engine.parse("""
                <h1>Quarkus and Qute</h1>
                {#ascii}
                == Qute and Roq
                Here is a list:
                {#for item in items}
                * an {item} as a list item
                {/for}
                {/ascii}
                """).data("items", List.of("apple", "banana", "cherry"))
                .render();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).contains("<h1>Quarkus and Qute</h1>");
            softly.assertThat(result).contains("<h2>Qute and Roq</h2>");
            softly.assertThat(result).contains("<ul>");
            softly.assertThat(result).contains("<li>");
        });
    }
}
