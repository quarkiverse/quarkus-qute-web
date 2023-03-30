package io.quarkiverse.quteserverpages.deployment.devui;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkiverse.quteserverpages.deployment.QspTemplatePathBuildItem;
import io.quarkiverse.quteserverpages.runtime.QspBuildTimeConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.vertx.core.json.JsonArray;

public class QspDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void pages(List<QspTemplatePathBuildItem> templatePaths, HttpRootPathBuildItem httpRootPath,
            QspBuildTimeConfig config, BuildProducer<CardPageBuildItem> cardPages) {

        CardPageBuildItem pageBuildItem = new CardPageBuildItem();

        JsonArray paths = new JsonArray();
        for (String path : templatePaths.stream().map(QspTemplatePathBuildItem::getPath)
                .sorted(Comparator.comparing(p -> p.toLowerCase())).collect(Collectors.toList())) {
            paths.add(path);
        }

        pageBuildItem.addBuildTimeData("paths", paths);
        pageBuildItem.addBuildTimeData("rootPrefix", httpRootPath.relativePath(config.rootPath) + "/");

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Pages")
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-qsp-paths.js")
                .staticLabel(String.valueOf(paths.size())));

        cardPages.produce(pageBuildItem);
    }

}
