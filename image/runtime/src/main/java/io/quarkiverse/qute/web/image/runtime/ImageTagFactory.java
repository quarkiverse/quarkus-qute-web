package io.quarkiverse.qute.web.image.runtime;

import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.Parameter;
import io.quarkus.qute.UserTagSectionHelper;

/**
 * {@code {#img}} is a user tag backed by {@code templates/qute-image/img.html}: the markup lives in the template
 * and the lookup in the {@code img} namespace. Unlike a tag discovered under {@code tags/}, it declares its own
 * parameters, so {@code {#img '/photo.jpg' /}} and {@code {#img src=page.image /}} both reach the template as
 * {@code src}, and {@code preset} is always set.
 */
@EngineConfiguration
public class ImageTagFactory extends UserTagSectionHelper.Factory {

    public ImageTagFactory() {
        super("img", "qute-image/img");
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder()
                .addParameter("src")
                .addParameter(Parameter.builder("preset").defaultValue("'default'").build())
                .checkNumberOfParams(false)
                .build();
    }
}
