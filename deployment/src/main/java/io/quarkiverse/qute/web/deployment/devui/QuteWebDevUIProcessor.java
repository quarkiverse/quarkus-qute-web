package io.quarkiverse.qute.web.deployment.devui;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.deployment.QuteWebTemplatePathBuildItem;
import io.quarkiverse.qute.web.runtime.QuteWebBuildTimeConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.vertx.core.json.JsonArray;

public class QuteWebDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void pages(List<QuteWebTemplatePathBuildItem> templatePaths, HttpRootPathBuildItem httpRootPath,
            QuteWebBuildTimeConfig config, BuildProducer<CardPageBuildItem> cardPages) {

        CardPageBuildItem pageBuildItem = new CardPageBuildItem();

        JsonArray paths = new JsonArray();
        for (String path : templatePaths.stream().map(QuteWebTemplatePathBuildItem::getPath)
                .sorted(Comparator.comparing(p -> p.toLowerCase())).collect(Collectors.toList())) {
            paths.add(path);
        }

        pageBuildItem.addBuildTimeData("paths", paths);
        pageBuildItem.addBuildTimeData("rootPrefix", httpRootPath.relativePath(config.rootPath()) + "/");

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Pages")
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-qsp-paths.js")
                .staticLabel(String.valueOf(paths.size())));

        cardPages.produce(pageBuildItem);
    }

}
