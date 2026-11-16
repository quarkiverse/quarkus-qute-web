package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.ImageOptions;
import io.quarkiverse.qute.web.image.converter.ImageSizing;
import io.quarkiverse.qute.web.image.converter.UnsupportedFormatException;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageStats;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

/**
 * The conversion of one build: resolves each source (the image dirs index, the template's directory or classpath
 * location, a download, or bytes handed over), registers the usage, and writes the variants into the build dir,
 * reusing the ones already there.
 */
final class ImageConversion {

    private static final Logger LOGGER = Logger.getLogger(ImageConversion.class);

    /** One usage of a source: the template it was found in (null for a host item or a glob), what it declared, its preset. */
    record Usage(String templateId, String declaredPath, String originalUrl, String presetName, PresetConfig preset) {
    }

    private final ImageConfig config;
    private final ImageConverter converter;
    private final ImagesBuildItem images;
    private final Path buildDir;
    private final Map<String, ProjectFile> indexedFiles;
    private final BuildProducer<GeneratedStaticResourceBuildItem> staticResources;
    // null when remote sources are off: a remote source then renders as is
    private final RemoteImages remote;
    // the local sources, for dev mode to watch (a download is not a source to watch)
    private final Set<String> watchedSources = new LinkedHashSet<>();

    ImageConversion(ImageConfig config, ImageConverter converter, ImagesBuildItem images, Path buildDir,
            Map<String, ProjectFile> indexedFiles, BuildProducer<GeneratedStaticResourceBuildItem> staticResources) {
        this.config = config;
        this.converter = converter;
        this.images = images;
        this.buildDir = buildDir;
        this.indexedFiles = indexedFiles;
        this.staticResources = staticResources;
        this.remote = config.remote().enabled()
                ? new RemoteImages(buildDir, converter, RemoteImages.Options.from(config), images.stats())
                : null;
    }

    /** The paths dev mode watches: the indexed files as the scanner reports them, and the files next to templates. */
    Set<String> watchedSources() {
        return watchedSources;
    }

    /**
     * Resolve a preset by name; "default" falls back to the built-in default preset when not configured.
     *
     * @param context where the preset was referenced, for the error message
     */
    PresetConfig preset(String name, String context) {
        return config.preset(name).orElseThrow(() -> new RuntimeException(
                "Unknown preset '%s' in %s. Available presets (quarkus.qute.image.presets.*): %s"
                        .formatted(name, context, config.presets().keySet())));
    }

    /**
     * A literal usage in a template: a remote url, an absolute path from the image dirs, or a path relative to the
     * template (its directory on disk, or its directory on the classpath for a template packaged in a jar).
     */
    void collectImage(ImageRef tag, Path templatePath, String resourceDir, String templateName) {
        String src = tag.fileParam();
        ResolvedSourceImage resolved;
        if (ImageUtils.isRemote(src)) {
            resolved = resolveRemote(src);
            if (resolved == null) {
                return;
            }
        } else if (src.startsWith("/")) {
            resolved = resolveFromIndex(src);
        } else if (templatePath != null) {
            resolved = resolveRelative(templatePath.getParent(), src, templateName);
        } else if (resourceDir != null) {
            resolved = resolveRelativeClasspath(resourceDir, src, templateName);
        } else {
            throw new RuntimeException("Cannot resolve relative image path '%s' in template '%s'."
                    .formatted(src, templateName)
                    + " Classpath templates (e.g. from themes) must use absolute image paths like '/images/photo.jpg'.");
        }
        // an absolute path is served by this extension under the root path, a relative one resolves in the page
        String originalUrl = src.startsWith("/") ? images.urlPrefix() + src : src;
        PresetConfig preset = preset(tag.presetName(), "%s in template '%s'".formatted(tag.context(), templateName));
        processImage(new Usage(templateName, src, originalUrl, tag.presetName(), preset), resolved);
    }

    /** A source handed over by another extension: the host owns the declared path, it is the url as it serves it. */
    void processItem(QuteImageToProcessBuildItem item) {
        PresetConfig preset = preset(item.presetName(), "QuteImageToProcessBuildItem " + item.declaredPath());
        ResolvedSourceImage resolved = item.isRemote() ? resolveRemote(item.declaredPath()) : resolveItem(item);
        if (resolved != null) {
            LOGGER.debugf("Processing image from build item: %s with preset %s", item, item.presetName());
            processImage(new Usage(null, item.declaredPath(), item.declaredPath(), item.presetName(), preset), resolved);
        }
    }

    /** A glob match: keyed by its url, the key a template lookup uses, and served under the root path. */
    void processGlobMatch(ProjectFile file, String declaredPath, String presetName, String glob) {
        PresetConfig preset = preset(presetName, "generate pattern (glob: %s)".formatted(glob));
        LOGGER.debugf("Glob match: %s with preset %s", file.scopedPath(), presetName);
        processImage(new Usage(null, declaredPath, images.urlPrefix() + declaredPath, presetName, preset),
                toResolvedImage(file));
    }

    /** Registers the usage, then writes the variants of its preset that are not in the build dir yet. */
    void processImage(Usage usage, ResolvedSourceImage source) {
        ImageBuilder image = images.builder().addImage(source);
        images.builder().scannedImageTag(usage.templateId(), usage.declaredPath(), usage.originalUrl(),
                usage.presetName(), usage.preset(), image);
        PresetConfig preset = usage.preset();
        PresetConfig.Crop crop = preset.crop().orElse(null);
        String sourceExt = source.id().extension();

        ImageInfo info = isPassThrough(sourceExt) ? null : readInfoOrNull(source.contents(), usage.declaredPath());
        if (info != null && info.frames() > 1) {
            LOGGER.debugf("Image '%s' is animated (%d frames): used as is", usage.declaredPath(), info.frames());
            info = null;
        }
        if (info == null) {
            image.passThrough(sourceExt);
            images.stats().passedThrough();
            return;
        }
        image.info(new OriginalInfo(info.format(), info.width(), info.height()));

        ImageStats.Progress before = images.stats().progress();
        Set<Integer> widths = ImageUtils.effectiveWidths(preset.widths(), info.width());
        for (String format : preset.normalizedFormats()) {
            String ext = ImageUtils.extensionFromFormat(format);
            if (!converter.supportsFormat(ext)) {
                LOGGER.warnf("Skipping format '%s': not supported by the active image converter."
                        + " For webp/avif support, add the quarkus-qute-web-image-vips extension (requires libvips).",
                        ext);
                continue;
            }
            // a width larger than the source becomes the source width, once: the preset always yields a variant
            for (int width : widths) {
                int height = ImageSizing.computeTargetHeight(width, info.width(), info.height(),
                        crop != null ? crop.ratio() : null);
                image.addGeneratedImage(config.outputPath(),
                        new GeneratedImageOptions(width, height, format, crop, preset.quality(), converter.id()),
                        config.slugifyOutput(), generated -> {
                            Path outputPath = ImageUtils.generatedImagePath(buildDir, generated);
                            if (isComplete(outputPath)) {
                                // the file name encodes the source digest and every setting, so it is current
                                LOGGER.debugf("Reusing %s", generated.outputPath());
                                images.stats().reused(generated.outputPath());
                            } else {
                                ImageOptions options = new ImageOptions(width, height, ext,
                                        crop != null ? new ImageOptions.CropOptions(crop.ratio(), crop.keep()) : null,
                                        preset.quality());
                                long start = System.currentTimeMillis();
                                try {
                                    writeAtomically(converter, source.contents(), options, outputPath);
                                } catch (RuntimeException e) {
                                    throw new RuntimeException("Failed to convert image '%s' to %dpx %s (preset '%s'): %s"
                                            .formatted(usage.declaredPath(), width, ext, usage.presetName(), e.getMessage()),
                                            e);
                                }
                                long millis = System.currentTimeMillis() - start;
                                long size = size(outputPath);
                                images.stats().generated(generated.outputPath(), size, millis);
                                LOGGER.debugf("Generated %s (%s in %s)", generated.outputPath(),
                                        ImageStats.bytes(size), ImageStats.millis(millis));
                            }
                            staticResources.produce(new GeneratedStaticResourceBuildItem(generated.outputPath(),
                                    outputPath));
                        });
            }
        }
        if (config.verbose()) {
            LOGGER.infof("Converted %s (%dx%d, %s) with %s: %s", usage.declaredPath(), info.width(), info.height(),
                    ImageStats.bytes(source.contents().length), usage.presetName(),
                    ImageStats.converted(before, images.stats().progress(),
                            preset.normalizedFormats().stream().map(ImageUtils::extensionFromFormat)
                                    .collect(Collectors.joining(", ")),
                            widths.stream().map(String::valueOf).collect(Collectors.joining(", "))));
        }
    }

    /** The header of a source, or null when the converter cannot read it: the source is then used as is. */
    private ImageInfo readInfoOrNull(byte[] contents, String declaredPath) {
        try {
            return converter.readInfo(contents);
        } catch (UnsupportedFormatException e) {
            LOGGER.warnf("Image '%s' cannot be read by the '%s' converter (%s): it is used as is, without generated"
                    + " variants. For WebP sources add the quarkus-qute-web-image-vips extension (requires libvips).",
                    declaredPath, converter.id(), e.getMessage());
            return null;
        }
    }

    private boolean isPassThrough(String extension) {
        return config.passthrough().stream().anyMatch(ext -> ext.equalsIgnoreCase(extension));
    }

    /**
     * A variant can be reused when its file exists and is not empty (an interrupted build leaves empty or partial
     * files; partial ones are avoided by {@link #writeAtomically}).
     */
    static boolean isComplete(Path outputPath) {
        try {
            return Files.isRegularFile(outputPath) && Files.size(outputPath) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Convert into a hidden sibling file with the same extension (converters pick the format from it) and move it
     * into place, so the build dir never holds a partially written variant under its final name: two builds may
     * share the build dir, and a failed conversion leaves nothing behind.
     */
    static void writeAtomically(ImageConverter converter, byte[] contents, ImageOptions options, Path outputPath) {
        Path tmp = outputPath.resolveSibling("." + System.nanoTime() + "-" + outputPath.getFileName());
        try {
            converter.process(contents, options, tmp);
            Files.move(tmp, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move generated image into place: " + outputPath, e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- resolving sources

    private ResolvedSourceImage resolveItem(QuteImageToProcessBuildItem item) {
        String pathKey = item.file() != null ? item.file().toAbsolutePath().normalize().toString()
                : "bytes:" + item.declaredPath();
        byte[] contents = item.contents();
        return new ResolvedSourceImage(images.builder().getImageId(pathKey, item.fileName(), contents), contents);
    }

    /**
     * A remote source as a local file, or null when remote sources are off or the download failed: the url then
     * renders as is (a failure is a warning, or an error when strict).
     */
    private ResolvedSourceImage resolveRemote(String url) {
        if (remote == null) {
            return null;
        }
        try {
            Path file = remote.fetch(url);
            byte[] contents = Files.readAllBytes(file);
            ImageId id = images.builder().getImageId(file.toString(), RemoteImages.fileName(url, file), contents);
            return new ResolvedSourceImage(id, contents);
        } catch (IOException e) {
            if (config.strict()) {
                throw new RuntimeException("Remote image '%s' cannot be converted: %s".formatted(url, e.getMessage()), e);
            }
            LOGGER.warnf("Remote image '%s' cannot be converted (%s): it is used as is, without generated variants",
                    url, e.getMessage());
            return null;
        }
    }

    private ResolvedSourceImage resolveFromIndex(String path) {
        ProjectFile file = indexedFiles.get(ImageUtils.indexKey(null, path));
        if (file == null) {
            throw new RuntimeException(
                    "Image does not exist or is not a file: " + path + " (looked up in indexed image directories)");
        }
        return toResolvedImage(file);
    }

    private ResolvedSourceImage toResolvedImage(ProjectFile file) {
        watchedSources.add(file.liveReloadWatchPath());
        byte[] contents = file.content();
        String name = StringPaths.fileName(file.scopedPath());
        // local files are keyed by their absolute path so that other extensions can match them by file
        String pathKey = file.isLocalFile() ? file.file().toAbsolutePath().normalize().toString() : file.indexPath();
        return new ResolvedSourceImage(images.builder().getImageId(pathKey, name, contents), contents);
    }

    /** A relative source stays under the directory of its template; "does not exist" would mislead when it is there. */
    private static void checkContained(String relativePath, String templateName) {
        Path path = Path.of(relativePath).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new RuntimeException("Relative image path '%s' in template '%s' escapes the template directory"
                    .formatted(relativePath, templateName));
        }
    }

    /** Next to the template, else under its {@code _images/}, else prefixed with an underscore. */
    private ResolvedSourceImage resolveRelative(Path parentDir, String relativePath, String templateName) {
        checkContained(relativePath, templateName);
        Path resolved = findRelativeImage(parentDir.normalize(), relativePath);
        if (resolved == null) {
            throw new RuntimeException("Image does not exist or is not a file: " + relativePath
                    + " (looked up at " + parentDir + ", also tried "
                    + ImageUtils.IMAGES_DIR + "/" + relativePath + " and _" + relativePath + ")");
        }
        try {
            byte[] contents = Files.readAllBytes(resolved);
            String pathKey = resolved.toAbsolutePath().normalize().toString();
            watchedSources.add(pathKey);
            return new ResolvedSourceImage(
                    images.builder().getImageId(pathKey, resolved.getFileName().toString(), contents), contents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image: " + resolved, e);
        }
    }

    private static Path findRelativeImage(Path parentDir, String relativePath) {
        Path resolved = ImageUtils.resolveContained(parentDir, relativePath);
        if (resolved == null) {
            return null;
        }
        if (Files.isRegularFile(resolved)) {
            return resolved;
        }
        Path inImagesDir = ImageUtils.resolveContained(parentDir.resolve(ImageUtils.IMAGES_DIR), relativePath);
        if (inImagesDir != null && Files.isRegularFile(inImagesDir)) {
            return inImagesDir;
        }
        Path prefixed = ImageUtils.resolveContained(parentDir, "_" + Path.of(relativePath).getFileName());
        return prefixed != null && Files.isRegularFile(prefixed) ? prefixed : null;
    }

    /** The same candidates as {@link #findRelativeImage}, for a template packaged in a jar. */
    private ResolvedSourceImage resolveRelativeClasspath(String resourceDir, String relativePath, String templateName) {
        checkContained(relativePath, templateName);
        String fileName = Path.of(relativePath).getFileName().toString();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String candidate : List.of(relativePath, ImageUtils.IMAGES_DIR + "/" + relativePath, "_" + fileName)) {
            String resource = StringPaths.toUnixPath(Path.of(resourceDir, candidate).normalize().toString());
            if (!resourceDir.isEmpty() && !resource.startsWith(resourceDir + "/")) {
                continue; // escapes the template directory
            }
            try (InputStream in = classLoader.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                byte[] contents = in.readAllBytes();
                return new ResolvedSourceImage(
                        images.builder().getImageId("classpath:" + resource, fileName, contents), contents);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read image resource: " + resource, e);
            }
        }
        throw new RuntimeException("Image does not exist or is not a file: " + relativePath
                + " (looked up on the classpath under '" + resourceDir + "', also tried "
                + ImageUtils.IMAGES_DIR + "/" + relativePath + " and _" + fileName + ")");
    }
}
