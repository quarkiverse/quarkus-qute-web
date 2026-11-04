package io.quarkiverse.qute.web.image.deployment.items.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.digest;
import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;

public class ImagesBuilder {

    // cache of absolute image basePath to image digest
    private final Map<String, ImageId> imageIdsByPath = new HashMap<>();
    // map of image (digest/file) to image
    private final Map<String, ImageBuilder> images = new HashMap<>();
    private final Map<String, ScannedImageTag> scannedImageTags = new HashMap<>();

    public Map<String, ScannedImageTag> scannedImageTags() {
        return scannedImageTags;
    }

    public Map<String, ImageTag> computeImageTags() {
        Map<String, ImageTag> computed = new HashMap<>();
        for (Map.Entry<String, ScannedImageTag> scanned : scannedImageTags.entrySet()) {
            computed.put(scanned.getKey(), scanned.getValue().toImageTag());
        }
        return computed;
    }

    public Map<String, Image> computeImages() {
        Map<String, Image> computed = new HashMap<>();
        for (Map.Entry<String, ImageId> e : imageIdsByPath.entrySet()) {
            if (computed.containsKey(e.getKey())) {
                throw new IllegalStateException(
                        "Duplicate basePath %s for image id %s".formatted(e.getKey(), e.getValue().key()));
            }
            if (!images.containsKey(e.getValue().key())) {
                throw new IllegalStateException("Image not found for basePath %s".formatted(e.getKey()));
            }
            final ImageBuilder i = images.get(e.getValue().key());
            computed.put(e.getKey(), i.build());
        }
        return computed;
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

    public AddImageResult addImage(ResolvedSourceImage resolvedImage) {
        /*
         * The idea here is that we want to make sure responsives for a unique absolute basePath end up in the same folder (same
         * digest,
         * same file baseName),
         * and if someone has the same file (same digest) in more than one absolute basePath, they also end up in the same
         * folder
         * (same
         * digest, same file baseName),
         * and if someone has the same file (same digest) in more than one absolute basePath under a different file baseName,
         * they
         * also
         * end
         * up in the same
         * folder (same digest), but with different file names.
         */
        AtomicBoolean created = new AtomicBoolean(false);
        final ImageBuilder image = images.computeIfAbsent(resolvedImage.id().key(), key2 -> {
            created.set(true);
            return new ImageBuilder(resolvedImage.id());
        });
        return new AddImageResult(image, created.get());
    }

    public record AddImageResult(ImageBuilder image, boolean created) {

    }

    public ScannedImageTag scannedImageTag(String templateId, String declaredPath, String presetName,
            PresetConfig config,
            ImageBuilder image) {
        final ScannedImageTag tag = new ScannedImageTag(templateId, declaredPath, config, image);
        scannedImageTags.put(imageTagKey(templateId, declaredPath, presetName),
                tag);
        return tag;
    }

}
