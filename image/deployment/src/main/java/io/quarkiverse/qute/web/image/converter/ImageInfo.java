package io.quarkiverse.qute.web.image.converter;

/**
 * What a converter reads from the header of a source.
 *
 * @param frames the number of frames or pages: more than one is an animation, which is used as is
 */
public record ImageInfo(String format, int width, int height, int frames) {
}
