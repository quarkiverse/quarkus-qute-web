package io.quarkiverse.qute.web.image.runtime;

import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

@Vetoed
public class Images {

    // map of tags (by key:templateid/file) to image user
    private final Map<String, ImageTag> tags;

    public Images(Map<String, ImageTag> tags) {
        this.tags = tags;
    }

    public ImageTag get(String templateId, String declaredURI) {
        return tags.get(imageTagKey(templateId, declaredURI));
    }

    public static String imageTagKey(String templateId, String declaredURI) {
        return templateId + "|" + declaredURI;
    }

    public static String keyImage(String id, String name) {
        return id + "|" + name;
    }

    public record ImageId(String digest, String name, String publicPath) {

        public String key() {
            return keyImage(digest, name);
        }
    }

    /**
     * This represents an image tag, pointing to a processed image
     */
    public record ImageTag(String templateId, String declaredPath, String publicPath, Image image) {
        // Eventually, this will list the variants in use

    }

    /**
     * This represents a unique image for a unique path along with all its generated variants
     */
    public record Image(ImageId id, Map<Integer, Variant> variants) {
        public String srcset() {
            StringBuilder sb = new StringBuilder();
            for (Variant variant : variants.values()) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(variant.path).append(" ").append(variant.width).append("w");
            }

            return sb.toString();
        }
    }

    /**
     * @param path this is relative to the target dir: static/processed-images/digest/filename-width.ext
     */
    public record Variant(int width, String path) {

    }

}
