package io.quarkiverse.quteserverpages.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class QspTemplatePathBuildItem extends MultiBuildItem {

    private final String path;

    public QspTemplatePathBuildItem(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

}
