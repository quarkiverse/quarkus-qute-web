package io.quarkiverse.qute.web.markdown.heading.deployment;

import io.quarkiverse.qute.web.markdown.heading.runtime.HeadingAnchorConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

class QuteWebMarkdownHeadingAnchorProcessor {

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.unremovableOf(HeadingAnchorConfiguration.class));
    }
}
