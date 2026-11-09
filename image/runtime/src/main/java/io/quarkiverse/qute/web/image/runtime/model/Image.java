package io.quarkiverse.qute.web.image.runtime.model;

import java.util.List;

/**
 * This represents a unique base image for a unique path along with all its generated images.
 *
 * @param passThrough true when the source is used as is, without generated variants: a vector format listed in
 *        {@code quarkus.qute.image.passthrough}, or a format the active converter cannot read. {@code info} then
 *        only knows the format, with 0 for the unknown dimensions.
 */
public record Image(ImageId id, OriginalInfo info, List<GeneratedImage> generated, boolean passThrough) {
}
