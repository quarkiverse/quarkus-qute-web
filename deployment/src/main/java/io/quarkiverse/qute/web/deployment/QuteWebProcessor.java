package io.quarkiverse.qute.web.deployment;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.runtime.QuteWebBuildTimeConfig;
import io.quarkiverse.qute.web.runtime.QuteWebExtensions;
import io.quarkiverse.qute.web.runtime.QuteWebRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.qute.deployment.TemplateFilePathsBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;

class QuteWebProcessor {

    private static final Logger LOG = Logger.getLogger(QuteWebProcessor.class);

    private static final String FEATURE = "qute-web";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        // It is not a bean but we need to make it a part of the bean archive index
        return new AdditionalBeanBuildItem(QuteWebExtensions.class);
    }

    @BuildStep
    public void collectTemplatePaths(TemplateFilePathsBuildItem templateFilePaths,
            QuteWebBuildTimeConfig config, BuildProducer<QuteWebTemplateBuildItem> paths) {
        String publicPathPrefix = "";
        String publicDir = config.publicDir();
        if (!publicDir.equals("/") && !publicDir.isBlank()) {
            publicPathPrefix = publicDir + "/";
        }
        Optional<Pattern> hiddenTemplates = config.hiddenTemplates();
        for (String path : templateFilePaths.getFilePaths()) {
            if (!path.startsWith(publicPathPrefix)) {
                continue;
            }
            if (hiddenTemplates.isPresent()
                    // Match the path relative to the publicPath
                    && hiddenTemplates.get().matcher(path.substring(publicPathPrefix.length())).matches()) {
                LOG.debugf("Template %s is hidden", path);
                continue;
            }
            LOG.debugf("Web template found: %s", path);
            paths.produce(new QuteWebTemplateBuildItem(path, null));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    public RouteBuildItem produceTemplatesRoute(QuteWebRecorder recorder, List<QuteWebTemplateBuildItem> templates,
            HttpRootPathBuildItem httpRootPath, QuteWebBuildTimeConfig config) {
        if (templates.isEmpty()) {
            // There are no templates to serve
            return null;
        }
        final var templateLinks = templates.stream().filter(QuteWebTemplateBuildItem::hasLink)
                .collect(Collectors.toMap(QuteWebTemplateBuildItem::link, QuteWebTemplateBuildItem::templatePath));
        final var templatePaths = templates.stream().filter(not(QuteWebTemplateBuildItem::hasLink))
                .map(QuteWebTemplateBuildItem::templatePath)
                .collect(Collectors.toSet());
        return httpRootPath.routeBuilder()
                .routeFunction(httpRootPath.relativePath(config.rootPath() + "/*"), recorder.initializeRoute())
                .handlerType(config.useBlockingHandler() ? HandlerType.BLOCKING : HandlerType.NORMAL)
                .handler(recorder.handler(httpRootPath.relativePath(config.rootPath()), templatePaths, templateLinks))
                .build();
    }
}
