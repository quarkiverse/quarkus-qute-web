package io.quarkiverse.qute.web.image.runtime.model;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

public record ImageId(String digest, String baseName, String extension) {

    public String key() {
        return ImageUtils.keyImage(digest, baseName);
    }
}
