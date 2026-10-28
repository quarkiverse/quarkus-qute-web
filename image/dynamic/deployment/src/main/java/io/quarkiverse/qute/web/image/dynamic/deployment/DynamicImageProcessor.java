package io.quarkiverse.qute.web.image.dynamic.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.dynamic.runtime.DynamicImageRecorder;
import io.quarkiverse.qute.web.image.dynamic.runtime.DynamicImageResolverImpl;
import io.quarkiverse.qute.web.image.dynamic.runtime.DynamicImageRuntimeConfig;
import io.quarkiverse.qute.web.image.dynamic.runtime.ImageDir;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

public class DynamicImageProcessor {

    private static final String FEATURE = "qute-web-image-dynamic";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(DynamicImageResolverImpl.class));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void initConfig(
            DynamicImageRecorder recorder,
            ImageConfig imageConfig,
            List<ImagesDirBuildItem> imageDirs,
            Optional<ImageConverterBuildItem> converterItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {

        List<ImageDir> dirs = imageDirs.stream()
                .map(d -> new ImageDir(d.prefix(), d.isLocal() ? d.localPath().toString() : null))
                .toList();

        Map<String, ResolvedPresetConfig> presets = new HashMap<>();
        presets.put("default", ResolvedPresetConfig.from(PresetConfig.DEFAULT));
        for (Map.Entry<String, PresetConfig> entry : imageConfig.presets().entrySet()) {
            presets.put(entry.getKey(), ResolvedPresetConfig.from(entry.getValue()));
        }

        boolean useVips = converterItem.isPresent();

        syntheticBeanProducer.produce(SyntheticBeanBuildItem.configure(DynamicImageRuntimeConfig.class)
                .supplier(recorder.createConfig(dirs, presets, useVips, imageConfig.slugifyOutput()))
                .scope(Singleton.class)
                .unremovable()
                .done());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    RouteBuildItem registerRoute(DynamicImageRecorder recorder) {
        return RouteBuildItem.builder()
                .route("/static/images/generated/*")
                .handler(recorder.createRouteHandler())
                .build();
    }
}
