package io.quarkiverse.qute.web.image.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class ImageTemplateExtension {

    public static boolean isDirectUrl(ImageTag it) {
        return it.config().directUrl();
    }

    public static boolean isPicture(ImageTag it) {
        return sources(it).size() > 1;
    }

    public static String imgSrc(ImageTag it) {
        return selectBest(it.image(), it.config().fallbackFormat(), it.image().info().format(), it.config().widths(),
                it.declaredPath());
    }

    public static String directUrl(ImageTag it) {
        return imgSrc(it);
    }

    public static String imgSrcset(ImageTag it) {
        String fmt = singleFormat(it);
        if (fmt == null)
            return "";
        return buildSrcset(it.image(), fmt, it.config().pixelRatio().orElse(null), it.image().info().format(),
                it.config().widths());
    }

    public static boolean hasImgSrcset(ImageTag it) {
        return !imgSrcset(it).isBlank();
    }

    public static List<Source> sources(ImageTag it) {
        List<String> formats = it.config().normalizedFormats();
        if (formats == null)
            return List.of();

        String originalFormat = it.image().info().format();
        List<Source> list = new ArrayList<>();
        for (String fmt : formats) {
            String srcset = buildSrcset(it.image(), fmt, it.config().pixelRatio().orElse(null), originalFormat,
                    it.config().widths());
            if (!srcset.isBlank()) {
                String type = format(fmt, originalFormat);
                list.add(new Source(srcset, type));
            }
        }
        return list;
    }

    private static String buildSrcset(Image img, String fmt, ResolvedPresetConfig.PixelRatioConfig pixelRatio,
            String originalFormat,
            List<Integer> presetWidths) {
        var widthSet = presetWidths != null && !presetWidths.isEmpty()
                ? new HashSet<>(presetWidths)
                : null;
        List<GeneratedImage> candidates = img.generated().stream()
                .filter(gi -> matchesFormat(gi, fmt, originalFormat))
                .filter(gi -> widthSet == null || widthSet.contains(gi.width()))
                .sorted(Comparator.comparingInt(GeneratedImage::width))
                .toList();

        if (candidates.isEmpty())
            return "";

        if (pixelRatio != null) {
            int base = pixelRatio.baseWidth();
            return joinWithComma(
                    candidates.stream()
                            .map(gi -> gi.outputPath() + " " + trimRatio((double) gi.width() / (double) base) + "x")
                            .distinct()
                            .collect(Collectors.toList()));
        } else {
            return joinWithComma(
                    candidates.stream()
                            .map(gi -> gi.outputPath() + " " + gi.width() + "w")
                            .distinct()
                            .collect(Collectors.toList()));
        }
    }

    private static String selectBest(Image img, String desiredFmt, String originalFormat, List<Integer> presetWidths,
            String declaredPath) {
        var widthSet = presetWidths != null && !presetWidths.isEmpty()
                ? new HashSet<>(presetWidths)
                : null;
        return img.generated().stream()
                .filter(gi -> matchesFormat(gi, desiredFmt, originalFormat))
                .filter(gi -> widthSet == null || widthSet.contains(gi.width()))
                .max(Comparator.comparingInt(GeneratedImage::width))
                .map(GeneratedImage::outputPath)
                .orElseGet(() -> img.generated().stream()
                        .filter(gi -> widthSet == null || widthSet.contains(gi.width()))
                        .max(Comparator.comparingInt(GeneratedImage::width))
                        .map(GeneratedImage::outputPath)
                        .orElse(declaredPath));
    }

    private static boolean matchesFormat(GeneratedImage gi, String fmt, String originalFormat) {
        if ("original".equalsIgnoreCase(fmt)) {
            return eq(gi.format(), originalFormat);
        }
        return eq(gi.format(), fmt);
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static String joinWithComma(List<String> parts) {
        return String.join(", ", parts);
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
        return srcs.get(0).type;
    }

    public static String format(String fmt, String originalFormat) {
        return fmt.equalsIgnoreCase("original") ? originalFormat : fmt;
    }

    public static final class Source {
        public final String srcset;
        public final String type;

        public Source(String srcset, String type) {
            this.srcset = srcset;
            this.type = type;
        }

        public String getSrcset() {
            return srcset;
        }

        public String getType() {
            return type;
        }
    }

}
