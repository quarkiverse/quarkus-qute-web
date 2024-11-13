package io.quarkiverse.qute.web.asciidoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.asciidoc.runtime.kroki.KrokiClient;
import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuarkusAsciidocPrerenderingTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.qute.web.asciidoc.prerender-diagram", "true");

    @Inject
    Engine engine;

    private KrokiClient krokiClient = new KrokiClient() {
        @Override
        public String convert(String diagramType, String format, String encodedPayload) {
            return "Converted";
        }
    };

    @Test
    void shouldPrerenderImage() {

        String result = engine.parse("""
                <h1>Quarkus and Qute</h1>
                {#ascii}
                == Qute and Diagram
                Here is a list:

                [.transparent,plantuml,target="wunderbar",format=svg]
                ----
                @startuml
                @enduml
                ----

                {/ascii}
                """).render();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).contains("<h1>Quarkus and Qute</h1>");
            softly.assertThat(result).contains("<h2>Qute and Diagram</h2>");
            softly.assertThat(result).containsPattern("<img src=.* class=\"transparent\"");
            softly.assertThat(result).doesNotContain("@startuml");
        });
    }
}
