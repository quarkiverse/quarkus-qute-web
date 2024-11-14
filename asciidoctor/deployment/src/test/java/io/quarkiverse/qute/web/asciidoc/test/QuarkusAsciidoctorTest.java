package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuarkusAsciidoctorTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest();

    @Inject
    Engine engine;

    @Test
    public void shouldConvertUsingAsciiTag() {

        String result = engine.parse("{#ascii}...{/ascii}").render();

        assertThat(result).contains("""
                <div class="paragraph">
                <p>&#8230;&#8203;</p>
                </div>""");
    }

    @Test
    public void shouldConvertUsingAsciidocTag() {

        String result = engine.parse("{#asciidoc}...{/asciidoc}").render();

        assertThat(result).contains("""
                <div class="paragraph">
                <p>&#8230;&#8203;</p>
                </div>""");
    }

    @Test
    public void testH1() {

        String result = engine.parse("{#ascii}= Quarkus and Roq{/ascii}").render();

        assertThat(result).contains("<h1>Quarkus and Roq</h1>");
    }

    @Test
    void shouldConvertWithForTagInsideAsciiTag() {

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
            softly.assertThat(result).contains("<h2 id=\"_qute_and_roq\">Qute and Roq</h2>");
            softly.assertThat(result).contains("<ul>");
            softly.assertThat(result).contains("<li>");
        });
    }
}
