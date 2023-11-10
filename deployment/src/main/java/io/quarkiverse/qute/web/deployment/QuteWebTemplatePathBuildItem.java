package io.quarkiverse.qute.web.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class QuteWebTemplatePathBuildItem extends MultiBuildItem {

    private final String path;

    public QuteWebTemplatePathBuildItem(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

}
