package io.quarkiverse.qute.web.markdown.deployment;

import io.quarkiverse.qute.web.markdown.runtime.MarkdownSectionHelperFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuteWebMarkdownProcessor {

    private static final String FEATURE = "qute-web-markdown";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(MarkdownSectionHelperFactory.class));
    }
}
