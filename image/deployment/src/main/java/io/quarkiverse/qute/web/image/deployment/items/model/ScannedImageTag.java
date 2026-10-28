package io.quarkiverse.qute.web.image.deployment.items.model;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;

public record ScannedImageTag(String templateId, String declaredPath, PresetConfig config, ImageBuilder image) {

    public ImageTag toImageTag() {
        return new ImageTag(templateId, declaredPath, ResolvedPresetConfig.from(config), image.build());
    }

}
