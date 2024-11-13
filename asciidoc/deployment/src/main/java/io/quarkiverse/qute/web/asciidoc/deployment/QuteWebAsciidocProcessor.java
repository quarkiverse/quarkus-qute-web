package io.quarkiverse.qute.web.asciidoc.deployment;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocConverter;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocRendererFactory;
import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocSectionHelperFactory;
import io.quarkiverse.qute.web.asciidoc.runtime.kroki.KrokiClient;
import io.quarkiverse.qute.web.asciidoc.runtime.kroki.ObjectMapperConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class QuteWebAsciidocProcessor {

    private static final String FEATURE = "qute-web-asciidoc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void addToIndex(BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClassesBuildItemBuildProducer) {
        additionalIndexedClassesBuildItemBuildProducer
                .produce(new AdditionalIndexedClassesBuildItem(KrokiClient.class.getName()));
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(
                AsciidocSectionHelperFactory.class,
                AsciidocRendererFactory.class,
                AsciidocConverter.class,
                ObjectMapperConfig.class));
    }

    @BuildStep
    void registerOpenPdfForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            CombinedIndexBuildItem combinedIndex) {
        final List<String> classNames = new ArrayList<>();
        classNames.add(io.yupiik.tools.ascii2svg.Svg.class.getName());

        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder(classNames.toArray(new String[0])).methods().fields().build());
    }

}
