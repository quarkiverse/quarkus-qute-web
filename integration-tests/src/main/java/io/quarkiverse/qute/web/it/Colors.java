package io.quarkiverse.qute.web.it;

import java.util.List;

import io.quarkus.qute.TemplateData;

@TemplateData
public class Colors {

    // Accessible via io_quarkiverse_qutepages_it_Colors:getColors
    public static List<String> getColors() {
        return List.of("red", "green", "blue");
    }

}
