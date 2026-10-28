package io.quarkiverse.qute.web.image.runtime.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.normalizeFormat;

public record OriginalInfo(String format, int width, int height) {

    public OriginalInfo(String format, int width, int height) {
        this.format = normalizeFormat(format);
        this.width = width;
        this.height = height;
    }

}
