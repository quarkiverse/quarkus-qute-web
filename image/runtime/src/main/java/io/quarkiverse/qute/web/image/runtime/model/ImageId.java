package io.quarkiverse.qute.web.image.runtime.model;

import java.io.Serializable;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

public record ImageId(String digest, String baseName, String extension) implements Serializable {

    public String key() {
        return ImageUtils.keyImage(digest, baseName);
    }
}
