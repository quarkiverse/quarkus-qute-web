package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ImageRecorder {
    public Supplier<Images> imagesSupplier(Map<String, Images.ImageTag> tags) {
        return () -> new Images(tags);

    }
}
