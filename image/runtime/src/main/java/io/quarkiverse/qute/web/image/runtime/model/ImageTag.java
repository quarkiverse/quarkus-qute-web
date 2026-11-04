package io.quarkiverse.qute.web.image.runtime.model;

import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;

public record ImageTag(String templateId, String declaredPath, ResolvedPresetConfig config,
        Image image) {
}
