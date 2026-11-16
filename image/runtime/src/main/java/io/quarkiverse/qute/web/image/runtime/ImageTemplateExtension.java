package io.quarkiverse.qute.web.image.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkus.qute.TemplateData;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class ImageTemplateExtension {

    public static boolean isDirectUrl(ImageTag it) {
        return it.config().directUrl();
    }

    public static boolean isPicture(ImageTag it) {
        return sources(it).size() > 1;
    }

    /**
     * The url of the {@code <img>} source: the best variant of the preset under the HTTP root path, or the
     * original when the source is passed through or no variant exists.
     */
    public static String imgSrc(ImageTag it) {
        if (it.image().passThrough()) {
            return it.originalUrl();
        }
        return bestVariant(it).map(variant -> it.urlPrefix() + variant.outputPath()).orElse(it.originalUrl());
    }

    /**
     * The largest variant of the preset, in its fallback format when one exists: the one {@code src}, {@code width}
     * and {@code height} come from. Empty for a passed-through source.
     */
    public static Optional<GeneratedImage> bestVariant(ImageTag it) {
        if (it.image().passThrough()) {
            return Optional.empty();
        }
        Set<Integer> widths = effectiveWidths(it);
        List<GeneratedImage> candidates = it.image().generated().stream()
                .filter(gi -> ofPreset(gi, it) && (widths.isEmpty() || widths.contains(gi.width())))
                .toList();
        Comparator<GeneratedImage> byWidth = Comparator.comparingInt(GeneratedImage::width);
        return candidates.stream()
                .filter(gi -> eq(gi.format(), it.config().fallbackFormat()))
                .max(byWidth)
                .or(() -> candidates.stream().max(byWidth));
    }

    /** The widths the preset produces for this source, see {@link ImageUtils#effectiveWidths(List, int)}. */
    private static Set<Integer> effectiveWidths(ImageTag it) {
        return ImageUtils.effectiveWidths(it.config().widths(), it.image().info().width());
    }

    /** Variants are shared by every preset using the image: a preset renders those produced with its settings. */
    private static boolean ofPreset(GeneratedImage gi, ImageTag it) {
        return gi.settings().equals(it.config().variantSettings());
    }

    public static String imgSrcset(ImageTag it) {
        String fmt = singleFormat(it);
        if (fmt == null)
            return "";
        return buildSrcset(it, fmt);
    }

    public static boolean hasImgSrcset(ImageTag it) {
        return !imgSrcset(it).isBlank();
    }

    public static List<Source> sources(ImageTag it) {
        List<String> formats = it.config().normalizedFormats();
        if (formats == null || it.image().passThrough())
            return List.of();

        List<Source> list = new ArrayList<>();
        for (String fmt : formats) {
            String srcset = buildSrcset(it, fmt);
            if (!srcset.isBlank()) {
                list.add(new Source(srcset, fmt));
            }
        }
        return list;
    }

    private static String buildSrcset(ImageTag it, String fmt) {
        Set<Integer> widths = effectiveWidths(it);
        List<GeneratedImage> candidates = it.image().generated().stream()
                .filter(gi -> ofPreset(gi, it) && eq(gi.format(), fmt))
                .filter(gi -> widths.isEmpty() || widths.contains(gi.width()))
                .sorted(Comparator.comparingInt(GeneratedImage::width))
                .toList();

        if (candidates.isEmpty())
            return "";

        ResolvedPresetConfig.PixelRatioConfig pixelRatio = it.config().pixelRatio();
        if (pixelRatio != null) {
            int base = pixelRatio.baseWidth();
            return candidates.stream()
                    .map(gi -> it.urlPrefix() + gi.outputPath() + " " + trimRatio((double) gi.width() / (double) base) + "x")
                    .distinct()
                    .collect(Collectors.joining(", "));
        }
        return candidates.stream()
                .map(gi -> it.urlPrefix() + gi.outputPath() + " " + gi.width() + "w")
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static String trimRatio(double r) {
        String s = String.format(Locale.ROOT, "%.2f", r);
        s = s.indexOf('.') >= 0 ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    private static String singleFormat(ImageTag it) {
        List<Source> srcs = sources(it);
        if (srcs.size() != 1)
            return null;
        return srcs.get(0).type();
    }

    /** One {@code <source>} of a {@code <picture>}. */
    @TemplateData
    public record Source(String srcset, String type) {
    }

}
