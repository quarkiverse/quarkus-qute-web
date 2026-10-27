package io.quarkiverse.qute.web.image.runtime.model;

import java.util.List;

/**
 * This represents a unique base image for a unique path along with all its generated images
 */
public record Image(ImageId id, OriginalInfo info, List<GeneratedImage> generated) {
}
