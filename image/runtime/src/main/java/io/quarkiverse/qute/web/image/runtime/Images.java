package io.quarkiverse.qute.web.image.runtime;

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

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("images")
public class Images {

    // cache of absolute image path to image digest
    Map<Path, ImageId> imageIdsByPath = new HashMap<>();
    // map of image (digest/file) to image
    Map<String, Image> images = new HashMap<>();
    // map of user (by key:templateid/file) to image user
    Map<String, ImageUser> users = new HashMap<>();

    public record ImageId(String digest, String name, String publicPath) {

        public String key() {
            return keyImage(digest, name);
        }
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
            return new Image(resolvedImage.id);
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
        return computeProcessedPath(id.name, id.digest, width);
    }

    public static String keyImage(String id, String name) {
        return id + "|" + name;
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

    // for static init
    public Image restoreImage(ImageId id) {
        Image image = new Image(id);
        images.put(id.key(), image);
        return image;
    }

    public Map<Image, Set<ImageUser>> collectImageUsers() {
        Map<Image, Set<ImageUser>> ret = new HashMap<>();
        for (var user : users.values()) {
            ret.computeIfAbsent(user.image, x -> new HashSet<>()).add(user);
        }
        return ret;
    }

    public void registerImageUser(String templateId, String declaredPath, String publicPath, Image image) {
        users.put(keyImageUser(templateId, declaredPath),
                new ImageUser(templateId, declaredPath, publicPath, image));
    }

    public ImageUser get(String templateId, String declaredURI) {
        return users.get(keyImageUser(templateId, declaredURI));
    }

    private static String keyImageUser(String templateId, String declaredURI) {
        return templateId + "|" + declaredURI;
    }

    /**
     * This represents an image tag, pointing to a processed image
     */
    public static class ImageUser {
        public final Image image;
        public final String publicPath;
        public final String declaredPath;
        public final String templateId;
        // Eventually, this will list the variants in use

        public ImageUser(String templateId, String declaredPath, String publicPath, Image image) {
            this.templateId = templateId;
            this.declaredPath = declaredPath;
            this.image = image;
            this.publicPath = publicPath;
        }
    }

    /**
     * This represents a unique image for a unique path along with all its generated variants
     */
    public static class Image {

        public final TreeMap<Integer, Variant> variants = new TreeMap<>();
        public final ImageId id;

        public Image(ImageId id) {
            this.id = id;
        }

        // for static init
        public void restoreVariants(Map<Integer, String> variants) {
            for (Map.Entry<Integer, String> entry : variants.entrySet()) {
                this.variants.put(entry.getKey(), new Variant(entry.getKey(), entry.getValue()));
            }
        }

        public Map<Integer, String> collectVariants() {
            Map<Integer, String> ret = new HashMap<>();
            for (Map.Entry<Integer, Variant> entry : variants.entrySet()) {
                ret.put(entry.getKey(), entry.getValue().path);
            }
            return ret;
        }

        public void addScaledImage(int width, Consumer<Variant> consumer) {
            variants.computeIfAbsent(width, key -> {
                Variant newVariant = new Variant(width);
                consumer.accept(newVariant);
                return newVariant;
            });
        }

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

        public class Variant {

            public final int width;
            // this is relative to the target dir: static/processed-images/digest/filename-width.ext
            public final String path;

            public Variant(int width) {
                this.width = width;
                this.path = computeProcessedPath(id, String.valueOf(width));
            }

            Variant(int width, String path) {
                this.width = width;
                this.path = path;
            }

        }

    }

    /**
     * represent a resolved image, which can be on the FS, or in a zip file, or in the classpath. This
     * is suboptimal, I'd rather use a ByteBuffer which can be memory mapped and avoid loading the image in memory
     * but the ImageIO API doesn't use it anyway, so it's moot.
     */
    public record ResolvedSourceImage(Path absolutePath, ImageId id, boolean served, byte[] contents) {

    }
}
