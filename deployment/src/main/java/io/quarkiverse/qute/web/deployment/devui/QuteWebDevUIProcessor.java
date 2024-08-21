package io.quarkiverse.qute.web.deployment.devui;

import java.util.Comparator;
import java.util.List;

import io.quarkiverse.qute.web.deployment.QuteWebTemplateBuildItem;
import io.quarkiverse.qute.web.runtime.PathUtils;
import io.quarkiverse.qute.web.runtime.QuteWebBuildTimeConfig;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class QuteWebDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void pages(List<QuteWebTemplateBuildItem> templatePaths, HttpRootPathBuildItem httpRootPath,
            QuteWebBuildTimeConfig config, BuildProducer<CardPageBuildItem> cardPages) {

        CardPageBuildItem pageBuildItem = new CardPageBuildItem();
        final String publicDir = config.publicDir();
        JsonArray paths = new JsonArray();
        for (QuteWebTemplateBuildItem item : templatePaths.stream()
                .sorted(Comparator.comparing(p -> p.templatePath().toLowerCase())).toList()) {
            var link = item.link() == null ? PathUtils.removeLeadingSlash(item.templatePath().replace(publicDir, ""))
                    : item.link();
            link = PathUtils.join(httpRootPath.relativePath(config.rootPath()), link);
            paths.add(new JsonObject().put("templateId", item.templatePath()).put("link", link));
        }
        pageBuildItem.addBuildTimeData("paths", paths);

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Pages")
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-qsp-paths.js")
                .staticLabel(String.valueOf(paths.size())));

        cardPages.produce(pageBuildItem);
    }

}
