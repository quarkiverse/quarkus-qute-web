package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ImageRecorder {
    public RuntimeValue<Images.Image> addImage(BeanContainer beanContainer, Images.ImageId id,
            Map<Integer, String> variants) {
        Images images = beanContainer.beanInstance(Images.class);
        Images.Image image = images.restoreImage(id);
        image.restoreVariants(variants);
        return new RuntimeValue<>(image);
    }

    public void addImageUser(BeanContainer beanContainer, String templateId, String declaredURI, String runtimeURI,
            RuntimeValue<Images.Image> image) {
        Images images = beanContainer.beanInstance(Images.class);
        images.registerImageUser(templateId, declaredURI, runtimeURI, image.getValue());
    }
}
