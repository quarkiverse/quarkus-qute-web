package io.quarkiverse.qute.web.image.deployment.items.model;

import io.quarkiverse.qute.web.image.runtime.model.ImageId;

/** A source as read: a local file, a classpath resource, a download, or bytes handed over by another extension. */
public record ResolvedSourceImage(ImageId id, byte[] contents) {
}
