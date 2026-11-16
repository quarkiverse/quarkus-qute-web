package io.quarkiverse.qute.web.image.deployment.items.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.digest;
import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;

/** The accumulator of a build: the images by digest and base name, and the usages (tags) by their lookup key. */
public class ImagesBuilder {

    // the id of each source by the path it was registered with: an absolute file, a classpath index path, a cached
    // download, or bytes:<declaredPath> for in-memory sources
    private final Map<String, ImageId> imageIdsByPath = new HashMap<>();
    // the same bytes under the same base name share one image, hence one set of variants
    private final Map<String, ImageBuilder> images = new HashMap<>();
    private final Map<String, ScannedImageTag> scannedImageTags = new HashMap<>();

    public Map<String, ImageTag> computeImageTags(String urlPrefix) {
        Map<String, ImageTag> computed = new HashMap<>();
        for (Map.Entry<String, ScannedImageTag> scanned : scannedImageTags.entrySet()) {
            computed.put(scanned.getKey(), scanned.getValue().toImageTag(urlPrefix));
        }
        return computed;
    }

    /** How many distinct images were added. */
    public int size() {
        return images.size();
    }

    public ImageId getImageId(String path, String fileName, byte[] content) {
        ImageId id = imageIdsByPath.get(path);
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx <= 0) {
            throw new IllegalArgumentException("Invalid image file name: '%s'".formatted(path));
        }
        if (id == null) {
            String baseName = fileName.substring(0, dotIdx);
            String extension = fileName.substring(dotIdx + 1);
            String digest = digest(content);
            id = new ImageId(digest, baseName, extension);
            imageIdsByPath.put(path, id);
        }
        return id;
    }

    public ImageBuilder addImage(ResolvedSourceImage resolvedImage) {
        return images.computeIfAbsent(resolvedImage.id().key(), key -> new ImageBuilder(resolvedImage.id()));
    }

    public void scannedImageTag(String templateId, String declaredPath, String originalUrl, String presetName,
            PresetConfig config, ImageBuilder image) {
        scannedImageTags.put(imageTagKey(templateId, declaredPath, presetName),
                new ScannedImageTag(templateId, declaredPath, originalUrl, config, image));
    }
}
