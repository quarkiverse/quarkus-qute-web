package io.quarkiverse.qute.web.image.runtime;

import java.util.function.Supplier;

import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ImageRecorder {

    /** The images recorded by the build as a serialized resource, see {@link Images#read(String)}. */
    public Supplier<Images> imagesSupplier(String resource) {
        return () -> Images.read(resource);
    }
}
