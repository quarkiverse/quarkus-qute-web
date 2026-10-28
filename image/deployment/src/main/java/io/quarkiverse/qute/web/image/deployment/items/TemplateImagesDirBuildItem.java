package io.quarkiverse.qute.web.image.deployment.items;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import io.quarkus.builder.item.SimpleBuildItem;

public final class TemplateImagesDirBuildItem extends SimpleBuildItem {
    private final Map<String, Set<Path>> templateImagesDirs;

    public TemplateImagesDirBuildItem(Map<String, Set<Path>> templateImagesDirs) {
        this.templateImagesDirs = templateImagesDirs;
    }

    public Map<String, Set<Path>> templateImagesDirs() {
        return templateImagesDirs;
    }
}
