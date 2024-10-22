package io.quarkiverse.qute.web.asciidoc.deployment;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.qute.web.asciidoc.runtime.AsciidocSectionHelperFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class QuteWebAsciidocProcessor {

    private static final String FEATURE = "qute-web-asciidoc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexTransitiveDependencies(BuildProducer<IndexDependencyBuildItem> index) {
        index.produce(new IndexDependencyBuildItem("io.yupiik.maven", "asciidoc-java"));
        index.produce(new IndexDependencyBuildItem("io.yupiik.maven", "ascii2svg"));
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(AsciidocSectionHelperFactory.class));
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