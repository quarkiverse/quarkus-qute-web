package io.quarkiverse.qute.web.image.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkus.qute.Template;

public final class ImageUtils {

    private ImageUtils() {
    }

    public static final String IMAGES_DIR = "_images";

    public static final String GLOB_TEMPLATE_ID = "*";

    public static String imageTagKey(String templateId, String declaredURI, String preset) {
        return (templateId != null ? templateId : GLOB_TEMPLATE_ID) + "|" + declaredURI + "|" + preset;
    }

    public static String normalizeFormat(String format) {
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

    public static String extensionFromFormat(String format) {
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

    public static String keyImage(String id, String name) {
        return id + "|" + name;
    }

    /**
     * @return true when the source is an absolute http(s) URL (downloaded at build time, see RemoteImages)
     */
    public static boolean isRemote(String src) {
        if (src == null) {
            return false;
        }
        String lower = src.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public static Path generatedImagePath(Path targetPath,
            GeneratedImage generatedImage) {
        return targetPath.resolve(generatedImage.outputPath().substring(1));
    }

    /**
     * The public path of a generated variant.
     *
     * @param outputPrefix the configured output path (for instance {@code /static/images/generated}), with a leading
     *        slash and no trailing slash
     * @param width the variant width
     */
    public static String computeOutputPath(String outputPrefix, ImageId id, int width, String settingsHash,
            String ext, boolean slugify) {
        final String ref = width + "-" + settingsHash;
        String name = slugify ? slugify(id.baseName()) : id.baseName();
        String fileName = "%s-%s.%s".formatted(name, ref, ext);
        return outputPrefix + "/" + shortDigest(id.digest()) + "/" + fileName;
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String stripped = normalized.replaceAll("\\p{M}", "");
        String lowered = stripped.toLowerCase(Locale.ROOT);
        String cleaned = lowered.replaceAll("[^a-z0-9]+", "-");
        cleaned = cleaned.replaceAll("^-|-$", "");
        return cleaned.isEmpty() ? "image" : cleaned;
    }

    /**
     * The id of the template a usage was scanned in: a fragment rendered on its own reports the fragment id, the
     * usage belongs to the template declaring it.
     */
    public static String templateId(Template template) {
        while (template instanceof Template.Fragment fragment) {
            template = fragment.getOriginalTemplate();
        }
        return template.getId();
    }

    /**
     * The preset settings that change the bytes of a variant besides its width and format, as a key shared by the
     * build (stored on the variant) and the render (computed from the preset) to keep each preset to its own
     * variants.
     */
    public static String variantSettings(String cropRatio, PresetConfig.Keep cropKeep, int quality) {
        return (cropRatio == null ? "" : cropRatio + "-" + cropKeep + "-") + quality;
    }

    /**
     * The widths a preset produces for a source: a width larger than the source is replaced by the source width,
     * once, so that a preset always yields a variant and a small source is never upscaled. Empty when the preset
     * has no widths.
     */
    public static Set<Integer> effectiveWidths(List<Integer> presetWidths, int sourceWidth) {
        Set<Integer> widths = new TreeSet<>();
        if (presetWidths != null) {
            for (int width : presetWidths) {
                widths.add(sourceWidth > 0 ? Math.min(width, sourceWidth) : width);
            }
        }
        return widths;
    }

    /** The full SHA-1 as hex: it keys images and cached downloads, where a collision would swap two images. */
    public static String digest(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(contents));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** The first 8 hex chars of a digest, for the generated paths where the name carries the rest. */
    public static String shortDigest(String digest) {
        return digest.length() > 8 ? digest.substring(0, 8) : digest;
    }

    public static String digest(String contents) {
        return digest(contents.getBytes(StandardCharsets.UTF_8));
    }

    public static String normalizeUnicode(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFC);
    }

    public static String indexKey(String urlPrefix, String path) {
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
    public static Path resolveContained(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        return resolved.startsWith(base) ? resolved : null;
    }

}
