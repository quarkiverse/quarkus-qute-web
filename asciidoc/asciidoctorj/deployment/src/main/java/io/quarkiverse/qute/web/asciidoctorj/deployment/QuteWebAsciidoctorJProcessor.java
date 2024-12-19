package io.quarkiverse.qute.web.asciidoctorj.deployment;

import io.quarkiverse.qute.web.asciidoctorj.runtime.AsciidoctorJConfig;
import io.quarkiverse.qute.web.asciidoctorj.runtime.AsciidoctorJConverter;
import io.quarkiverse.qute.web.asciidoctorj.runtime.AsciidoctorJSectionHelperFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuteWebAsciidoctorJProcessor {

    private static final String FEATURE = "qute-web-asciidoctorj";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans, AsciidoctorJConfig config) {
        additionalBeans.produce(new AdditionalBeanBuildItem(AsciidoctorJSectionHelperFactory.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(AsciidoctorJConverter.class));
    }

}
