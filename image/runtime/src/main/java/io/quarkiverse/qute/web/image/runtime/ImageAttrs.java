package io.quarkiverse.qute.web.image.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkus.qute.TemplateData;

/**
 * Routes tag-level attributes to target elements by prefix:
 * <ul>
 * <li>No prefix or {@code img-} prefix: applied to {@code <img>}</li>
 * <li>{@code picture-} prefix: applied to {@code <picture>}</li>
 * <li>{@code parent-} prefix: applied to {@code <picture>} if present, else {@code <img>}</li>
 * </ul>
 * Also injects defaults: {@code loading="lazy"}, {@code sizes="auto"}, and {@code width}/{@code height}
 * for CLS prevention. Tag-level attributes override defaults.
 */
@TemplateData
public record ImageAttrs(Map<String, String> imgMap, Map<String, String> pictureMap) {

    private static final String IMG_PREFIX = "img-";
    private static final String PICTURE_PREFIX = "picture-";
    private static final String PARENT_PREFIX = "parent-";

    /** From the attribute arguments of the {@code {#img}} user tag. */
    public static ImageAttrs from(Iterable<Map.Entry<String, Object>> args, ImageTag imageTag) {
        Map<String, String> tagAttrs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> arg : args) {
            if (arg.getValue() != null) {
                tagAttrs.put(arg.getKey(), arg.getValue().toString());
            }
        }
        return from(tagAttrs, imageTag);
    }

    private static ImageAttrs from(Map<String, String> tagAttrs, ImageTag imageTag) {
        Map<String, String> imgAttrs = new LinkedHashMap<>();
        Map<String, String> pictureAttrs = new LinkedHashMap<>();
        Map<String, String> parentAttrs = new LinkedHashMap<>();

        if (imageTag != null && !imageTag.config().directUrl()) {
            imgAttrs.put("loading", "lazy");

            // a pass-through image renders as is: no srcset so no sizes, and no dimensions since nothing was read
            if (!imageTag.image().passThrough()) {
                imgAttrs.put("sizes", "auto");
                // the dimensions of the variant the src points at, so the placeholder has the preset's ratio
                GeneratedImage fallback = ImageTemplateExtension.bestVariant(imageTag).orElse(null);
                if (fallback != null) {
                    imgAttrs.put("width", String.valueOf(fallback.width()));
                    imgAttrs.put("height", String.valueOf(fallback.height()));
                }
            }
        }

        for (Map.Entry<String, String> entry : tagAttrs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith(PICTURE_PREFIX)) {
                pictureAttrs.put(key.substring(PICTURE_PREFIX.length()), value);
            } else if (key.startsWith(PARENT_PREFIX)) {
                parentAttrs.put(key.substring(PARENT_PREFIX.length()), value);
            } else if (key.startsWith(IMG_PREFIX)) {
                imgAttrs.put(key.substring(IMG_PREFIX.length()), value);
            } else {
                imgAttrs.put(key, value);
            }
        }

        return new ImageAttrs(merge(imgAttrs, parentAttrs), merge(pictureAttrs, parentAttrs));
    }

    private static Map<String, String> merge(Map<String, String> targetAttrs, Map<String, String> parentAttrs) {
        Map<String, String> merged = new LinkedHashMap<>(parentAttrs);
        merged.putAll(targetAttrs);
        return merged;
    }
}
