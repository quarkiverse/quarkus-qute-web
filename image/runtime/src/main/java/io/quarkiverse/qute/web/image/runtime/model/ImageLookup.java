package io.quarkiverse.qute.web.image.runtime.model;

import io.quarkiverse.qute.web.image.runtime.ImageAttrs;
import io.quarkus.qute.TemplateData;

/**
 * What the {@code {#img}} user tag renders, resolved from its arguments by {@code img:lookup(_args)}.
 *
 * @param src the source: the {@code src} argument or the first unnamed one, null when absent or blank
 * @param image the image registered for the source and the preset, null when it was not converted
 * @param attrs the attributes of the rendered elements: the defaults for the image, overridden by the tag's
 *        other arguments
 */
@TemplateData
public record ImageLookup(String src, ImageTag image, ImageAttrs attrs) {
}
