package io.quarkiverse.qute.web.image.runtime;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;

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
public record ImageAttrs(Map<String, String> imgMap, Map<String, String> pictureMap) {

    private static final String IMG_PREFIX = "img-";
    private static final String PICTURE_PREFIX = "picture-";
    private static final String PARENT_PREFIX = "parent-";

    public static ImageAttrs from(Map<String, String> tagAttrs, ImageTag imageTag) {
        Map<String, String> imgAttrs = new LinkedHashMap<>();
        Map<String, String> pictureAttrs = new LinkedHashMap<>();
        Map<String, String> parentAttrs = new LinkedHashMap<>();

        if (imageTag != null && !imageTag.config().directUrl()) {
            imgAttrs.put("loading", "lazy");
            imgAttrs.put("sizes", "auto");

            GeneratedImage fallback = imageTag.image().generated().stream()
                    .max(Comparator.comparingInt(GeneratedImage::width))
                    .orElse(null);
            if (fallback != null) {
                imgAttrs.put("width", String.valueOf(fallback.width()));
                imgAttrs.put("height", String.valueOf(fallback.height()));
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
