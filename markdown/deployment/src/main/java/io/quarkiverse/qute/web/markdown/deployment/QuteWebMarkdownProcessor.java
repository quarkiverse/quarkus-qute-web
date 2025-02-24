package io.quarkiverse.qute.web.markdown.deployment;

import io.quarkiverse.qute.web.markdown.runtime.AutolinkConfiguration;
import io.quarkiverse.qute.web.markdown.runtime.HeadingAnchorConfiguration;
import io.quarkiverse.qute.web.markdown.runtime.MarkdownSectionHelperFactory;
import io.quarkiverse.qute.web.markdown.runtime.TablesConfiguration;
import io.quarkiverse.qute.web.markdown.runtime.commonmark.MdConfiguration;
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
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans, MarkdownConfig config) {

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        MarkdownSectionHelperFactory.class,
                        MdConfiguration.class)
                .setUnremovable()
                .build());

        if (config.autolink().enabled()) {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(AutolinkConfiguration.class));
        }

        if (config.headingAnchor().enabled()) {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(HeadingAnchorConfiguration.class));
        }

        if (config.tables().enabled()) {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(TablesConfiguration.class));
        }
    }
}
