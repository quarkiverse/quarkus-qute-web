package io.quarkiverse.qute.web.markdown.autolink.deployment;

import io.quarkiverse.qute.web.markdown.autolink.runtime.AutolinkConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuteWebMarkdownAutolinkProcessor {

    private static final String FEATURE = "qute-web-markdown-autolink";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(AutolinkConfiguration.class));
    }
}
