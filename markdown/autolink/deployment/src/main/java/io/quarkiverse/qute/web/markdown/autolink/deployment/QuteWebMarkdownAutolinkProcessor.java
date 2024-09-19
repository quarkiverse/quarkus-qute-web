package io.quarkiverse.qute.web.markdown.autolink.deployment;

import io.quarkiverse.qute.web.markdown.autolink.runtime.AutolinkConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

class QuteWebMarkdownAutolinkProcessor {

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(AutolinkConfiguration.class));
    }
}
