package io.quarkiverse.qute.web.markdown.tables.deployment;

import io.quarkiverse.qute.web.markdown.tables.runtime.TablesConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

class QuteWebMarkdownTablesProcessor {

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(TablesConfiguration.class));
    }
}
