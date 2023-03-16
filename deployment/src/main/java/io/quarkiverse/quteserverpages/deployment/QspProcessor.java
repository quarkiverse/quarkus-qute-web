package io.quarkiverse.quteserverpages.deployment;

import java.util.HashSet;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.quteserverpages.runtime.QspRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.qute.deployment.TemplateFilePathsBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

class QspProcessor {

    private static final Logger LOG = Logger.getLogger(QspProcessor.class);

    private static final String FEATURE = "qsp";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public RouteBuildItem produceTemplatesRoute(QspRecorder recorder, TemplateFilePathsBuildItem templateFilePaths,
            HttpRootPathBuildItem httpRootPath, QspBuildTimeConfig config) {

        Set<String> templatePaths = new HashSet<>(templateFilePaths.getFilePaths().size());
        for (String path : templateFilePaths.getFilePaths()) {
            if (config.hiddenTemplates.matcher(path).matches()) {
                LOG.debugf("Template %s is hidden", path);
            } else {
                templatePaths.add(path);
            }
        }
        return httpRootPath.routeBuilder()
                .routeFunction(httpRootPath.relativePath(config.rootPath + "/*"), recorder.initializeRoute())
                .handler(recorder.handler(httpRootPath.relativePath(config.rootPath), templatePaths))
                .build();
    }
}
