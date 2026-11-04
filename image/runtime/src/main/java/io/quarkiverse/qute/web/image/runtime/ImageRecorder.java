package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ImageRecorder {
    public Supplier<Images> imagesSupplier(Map<String, ImageTag> tags, Map<String, Image> images) {
        return () -> new Images(tags, images);

    }
}
