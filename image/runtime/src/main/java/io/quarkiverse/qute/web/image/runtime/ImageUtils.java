package io.quarkiverse.qute.web.image.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;

public interface ImageUtils {
    String IMAGES_DIR = "_images";

    Map<String, String> FORMAT_EXTENSION = Map.of(
            "jpeg", "jpg",
            "tiff", "tif");

    String GLOB_TEMPLATE_ID = "*";

    static String imageTagKey(String templateId, String declaredURI, String preset) {
        return (templateId != null ? templateId : GLOB_TEMPLATE_ID) + "|" + declaredURI + "|" + preset;
    }

    static String normalizeFormat(String format) {
        if (format == null || format.isBlank())
            return "unknown";

        if (format.startsWith("image/") || format.startsWith("application/")) {
            return format.toLowerCase(Locale.ROOT);
        }

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            case "heic", "heif" -> "image/heic"; // HEIC/HEIF
            case "gif" -> "image/gif";
            case "tif", "tiff" -> "image/tiff";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf"; // libvips can render PDFs
            case "jp2", "j2k", "jpx" -> "image/jp2"; // JPEG 2000
            case "jxl" -> "image/jxl"; // JPEG XL
            case "eps" -> "application/postscript"; // EPS
            case "psd" -> "image/vnd.adobe.photoshop"; // Photoshop
            case "ico" -> "image/x-icon";
            default -> "image/" + format.toLowerCase(Locale.ROOT);
        };

    }

    static String extensionFromFormat(String format) {
        if (format == null || format.isBlank())
            return "unknown";

        String f = format.toLowerCase(Locale.ROOT);

        // Strip "image/" or "application/" prefix if present
        if (f.startsWith("image/")) {
            f = f.substring("image/".length());
        } else if (f.startsWith("application/")) {
            f = f.substring("application/".length());
        }

        return switch (f) {
            case "jpeg" -> "jpg"; // prefer jpg for jpeg
            case "png" -> "png";
            case "webp" -> "webp";
            case "avif" -> "avif";
            case "heic", "heif" -> "heic";
            case "gif" -> "gif";
            case "tiff" -> "tif"; // common short form
            case "bmp" -> "bmp";
            case "svg+xml", "svg" -> "svg";
            case "pdf" -> "pdf";
            case "jp2" -> "jp2";
            case "jxl" -> "jxl";
            case "postscript", "eps" -> "eps";
            case "vnd.adobe.photoshop", "psd" -> "psd";
            case "x-icon", "ico" -> "ico";
            default -> f; // fallback: use the raw format string
        };
    }

    static String keyImage(String id, String name) {
        return id + "|" + name;
    }

    /**
     * @return true when the source is an absolute http(s) URL, which is never converted and always rendered as is
     */
    static boolean isRemote(String src) {
        if (src == null) {
            return false;
        }
        String lower = src.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    static Path generatedImagePath(Path targetPath,
            GeneratedImage generatedImage) {
        return targetPath.resolve(generatedImage.outputPath().substring(1));
    }

    static String removeExtension(String path) {
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(0, i) : path;
    }

    /**
     * The public path of a generated variant.
     *
     * @param outputPrefix the configured output path (for instance {@code /static/images/generated}), with a leading
     *        slash and no trailing slash
     * @param width the variant width, or null for the original
     */
    static String computeOutputPath(String outputPrefix, ImageId id, Integer width, String settingsHash,
            String ext, boolean slugify) {
        final String ref = width == null ? "original" : width + "-" + settingsHash;
        String name = slugify ? slugify(id.baseName()) : id.baseName();
        String fileName = "%s-%s.%s".formatted(name, ref, ext);
        return outputPrefix + "/" + id.digest() + "/" + fileName;
    }

    static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String stripped = normalized.replaceAll("\\p{M}", "");
        String lowered = stripped.toLowerCase(Locale.ROOT);
        String cleaned = lowered.replaceAll("[^a-z0-9]+", "-");
        cleaned = cleaned.replaceAll("^-|-$", "");
        return cleaned.isEmpty() ? "image" : cleaned;
    }

    static String digest(byte[] contents) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] digest = messageDigest.digest(contents);
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; ++i) {
                sb.append(Integer.toHexString(digest[i] & 255 | 256), 1, 3);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static String digest(String contents) {
        return digest(contents.getBytes(StandardCharsets.UTF_8));
    }

    static String getOutputExt(ImageId id, String format) {
        if (format.equalsIgnoreCase("original")) {
            return id.extension();
        }
        return extensionFromFormat(format);
    }

    static String normalizeUnicode(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFC);
    }

    static String indexKey(String urlPrefix, String path) {
        String stripped = path.startsWith("/") ? path.substring(1) : path;
        String key = urlPrefix != null && !urlPrefix.isEmpty()
                ? urlPrefix + "/" + stripped
                : stripped;
        return normalizeUnicode(key);
    }

    /**
     * Resolve a relative path against a base directory and verify it stays contained within it.
     *
     * @return the normalized resolved path, or null if it escapes the base directory
     */
    static Path resolveContained(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        return resolved.startsWith(base) ? resolved : null;
    }

}
