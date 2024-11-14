package io.quarkiverse.qute.web.asciidoc.deployment;

import io.quarkiverse.qute.web.asciidoc.runtime.AsciidoctorConfig;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidoctorConverter;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidoctorSectionHelperFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuteWebAsciidocProcessor {

    private static final String FEATURE = "qute-web-asciidoctor";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(
                AsciidoctorSectionHelperFactory.class,
                AsciidoctorConverter.class,
                AsciidoctorConfig.class));
    }

}
