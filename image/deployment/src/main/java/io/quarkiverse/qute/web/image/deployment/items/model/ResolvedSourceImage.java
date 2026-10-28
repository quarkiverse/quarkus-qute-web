package io.quarkiverse.qute.web.image.deployment.items.model;

import io.quarkiverse.qute.web.image.runtime.model.ImageId;

/**
 * represent a resolved image, which can be on the FS, or in a zip file, or in the classpath. This
 * is suboptimal, I'd rather use a ByteBuffer which can be memory mapped and avoid loading the image in memory
 * but the ImageIO API doesn't use it anyway, so it's moot.
 */
public record ResolvedSourceImage(String path, ImageId id, byte[] contents) {

}
