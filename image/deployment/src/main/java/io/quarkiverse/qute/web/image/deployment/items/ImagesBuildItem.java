package io.quarkiverse.qute.web.image.deployment.items;

import static io.quarkiverse.qute.web.image.runtime.Images.imageTagKey;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.quarkiverse.qute.web.image.runtime.Images.Image;
import io.quarkiverse.qute.web.image.runtime.Images.ImageId;
import io.quarkiverse.qute.web.image.runtime.Images.ImageTag;
import io.quarkiverse.qute.web.image.runtime.Images.Variant;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ImagesBuildItem extends SimpleBuildItem {

    // cache of absolute image path to image digest
    private final Map<Path, ImageId> imageIdsByPath = new HashMap<>();
    // map of image (digest/file) to image
    private final Map<String, Image> images = new HashMap<>();
    // map of user (by key:templateid/file) to image user
    private final Map<String, ImageTag> tags = new HashMap<>();

    public Map<String, ImageTag> tags() {
        return tags;
    }

    public ImageId getImageId(Path path, String name, byte[] content, String publicPath) {
        final Path normalize = path.normalize();
        ImageId id = imageIdsByPath.get(normalize);
        if (id == null) {
            String digest = digest(content);
            String effectivePublicPath = publicPath != null ? publicPath : computeProcessedPath(name, digest, null);
            id = new ImageId(digest, name, effectivePublicPath);
            imageIdsByPath.put(normalize, id);
        }
        return id;
    }

    public AddImageResult addImage(ResolvedSourceImage resolvedImage) {
        /*
         * The idea here is that we want to make sure responsives for a unique absolute path end up in the same folder (same
         * digest,
         * same file name),
         * and if someone has the same file (same digest) in more than one absolute path, they also end up in the same folder
         * (same
         * digest, same file name),
         * and if someone has the same file (same digest) in more than one absolute path under a different file name, they also
         * end
         * up in the same
         * folder (same digest), but with different file names.
         */
        AtomicBoolean created = new AtomicBoolean(false);
        final Image image = images.computeIfAbsent(resolvedImage.id().key(), key2 -> {
            created.set(true);
            return new Image(resolvedImage.id(), new TreeMap<>());
        });
        return new AddImageResult(image, created.get());
    }

    public record AddImageResult(Image image, boolean created) {

    }

    public static String computeProcessedPath(String fileName, String id, String width) {
        int lastDot = fileName.lastIndexOf('.');
        String newFileName;
        final String variantName = width == null ? "original" : width;
        if (lastDot != -1) {
            newFileName = "%s_%s%s".formatted(fileName.substring(0, lastDot), variantName, fileName.substring(lastDot));
        } else {
            newFileName = "%s_%s".formatted(fileName, variantName);
        }
        return Path.of("/static", "processed-images", id, newFileName).toString().replace('\\', '/');
    }

    private static String computeProcessedPath(ImageId id, String width) {
        return computeProcessedPath(id.name(), id.digest(), width);
    }

    private static String digest(byte[] contents) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] digest = messageDigest.digest(contents);
            // only keep the first 8 chars (4 bytes)
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; ++i) {
                sb.append(Integer.toHexString(digest[i] & 255 | 256).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<Image, Set<ImageTag>> getImageTags() {
        Map<Image, Set<ImageTag>> ret = new HashMap<>();
        for (var user : tags.values()) {
            ret.computeIfAbsent(user.image(), x -> new HashSet<>()).add(user);
        }
        return ret;
    }

    public void registerImageTag(String templateId, String declaredPath, String publicPath, Image image) {
        tags.put(imageTagKey(templateId, declaredPath),
                new ImageTag(templateId, declaredPath, publicPath, image));
    }

    public static void addScaledImage(Image image, int width, Consumer<Variant> consumer) {
        image.variants().computeIfAbsent(width, key -> {
            Variant newVariant = new Variant(width, computeProcessedPath(image.id(), String.valueOf(width)));
            consumer.accept(newVariant);
            return newVariant;
        });
    }

    /**
     * represent a resolved image, which can be on the FS, or in a zip file, or in the classpath. This
     * is suboptimal, I'd rather use a ByteBuffer which can be memory mapped and avoid loading the image in memory
     * but the ImageIO API doesn't use it anyway, so it's moot.
     */
    public record ResolvedSourceImage(Path absolutePath, ImageId id, boolean served, byte[] contents) {

    }
}
