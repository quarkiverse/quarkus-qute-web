package io.quarkiverse.qute.web.image.runtime;

import io.quarkiverse.qute.web.image.runtime.model.ImageTag;

/**
 * Resolves images at runtime for paths not pre-processed at build time.
 * When the static {@link io.quarkiverse.qute.web.image.runtime.model.Images} registry
 * has no entry for a given src+preset, this resolver attempts to find the source image,
 * convert it, and return an {@link ImageTag}.
 */
public interface DynamicImageResolver {

    /**
     * Attempt to resolve an image at runtime.
     *
     * @param src the image source path (e.g. "/static/images/photo.jpg")
     * @param preset the preset name (e.g. "default", "small")
     * @return the resolved ImageTag, or null if the image cannot be found
     */
    ImageTag resolve(String src, String preset);
}
