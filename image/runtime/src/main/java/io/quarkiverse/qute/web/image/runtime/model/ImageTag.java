package io.quarkiverse.qute.web.image.runtime.model;

import java.io.Serializable;

import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkus.qute.TemplateData;

/**
 * An image usage: where it was found, what it declared, the preset, and the image with its variants.
 *
 * @param templateId the template the usage was scanned in, null for an image handed over by another extension
 *        or matched by a glob
 * @param declaredPath the source as declared in the template, or the path handed over by another extension
 * @param urlPrefix the HTTP root path (without trailing slash, empty at the root) that prefixes the generated
 *        variants' paths in rendered urls
 * @param originalUrl the url of the original: the declared path with the root path when this extension serves
 *        it, the declared path unchanged when it is relative or another extension owns it
 */
@TemplateData
public record ImageTag(String templateId, String declaredPath, ResolvedPresetConfig config, Image image,
        String urlPrefix, String originalUrl) implements Serializable {
}
