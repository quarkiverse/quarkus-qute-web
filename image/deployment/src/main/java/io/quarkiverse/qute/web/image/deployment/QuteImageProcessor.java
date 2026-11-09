package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageIOConverter;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.ImageOptions;
import io.quarkiverse.qute.web.image.converter.ImageSizing;
import io.quarkiverse.qute.web.image.converter.UnsupportedFormatException;
import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImagesBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageNamespaceResolver;
import io.quarkiverse.qute.web.image.runtime.ImageRecorder;
import io.quarkiverse.qute.web.image.runtime.ImageSectionHelperFactory;
import io.quarkiverse.qute.web.image.runtime.ImageTemplateExtension;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ScanDeclarationBuildItem;
import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class QuteImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteImageProcessor.class);

    @BuildStep
    void initBeans(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageSectionHelperFactory.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageNamespaceResolver.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageTemplateExtension.class));
    }

    @BuildStep
    void wireScannerItems(
            List<ImagesDirBuildItem> imageDirs,
            BuildProducer<ScanDeclarationBuildItem> declarationProducer,
            BuildProducer<ScanLocalDirBuildItem> localDirProducer) {
        for (ImagesDirBuildItem dir : imageDirs) {
            String scope = scannerScope(dir);
            declarationProducer.produce(ScanDeclarationBuildItem.of(scope));
            if (dir.isLocal()) {
                localDirProducer.produce(new ScanLocalDirBuildItem(dir.localPath()));
            }
        }
    }

    @BuildStep
    ImagesBuildItem processTemplatesWithImages(
            ImageConfig imageConfig,
            Optional<ImageConverterBuildItem> converterItem,
            List<QuteImageTemplateToScanBuildItem> templateToScan,
            List<QuteImageToProcessBuildItem> toProcess,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            QuteImageTargetDirBuildItem targetDir,
            List<ImagesDirBuildItem> imageSourceDirs,
            ProjectScannerBuildItem scanner) {
        String outputPath = imageConfig.outputPath();
        if (!outputPath.startsWith("/") || outputPath.endsWith("/")) {
            throw new IllegalArgumentException(
                    "quarkus.qute.image.output-path must start with '/' and not end with '/', got: " + outputPath);
        }
        boolean hasGlobPatterns = !imageConfig.generate().isEmpty();
        if (templateToScan.isEmpty() && !hasGlobPatterns && toProcess.isEmpty()) {
            return null;
        }
        ImagesBuildItem images = new ImagesBuildItem();
        ImageConverter converter = converterItem.map(ImageConverterBuildItem::get).orElse(new ImageIOConverter());

        Map<String, ProjectFile> indexedFiles = indexImageFiles(imageSourceDirs, scanner);

        for (QuteImageTemplateToScanBuildItem template : templateToScan) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Inspecting (run-time) template %s for image tags", template.id);
            }
            Path templatePath = resolveSourcePath(template);
            for (ImageRef tag : template.imageRefs) {
                collectImage(staticResourceProducer, converter, indexedFiles, images, tag,
                        templatePath, template.id, targetDir.path, imageConfig);
            }
        }

        if (hasGlobPatterns) {
            processGlobPatterns(imageConfig, imageSourceDirs, scanner, converter,
                    staticResourceProducer, images, targetDir.path);
        }

        for (QuteImageToProcessBuildItem item : toProcess) {
            PresetConfig preset = resolvePreset(imageConfig, item.presetName(),
                    "QuteImageToProcessBuildItem " + item.declaredPath());
            String pathKey = item.file() != null ? item.file().toAbsolutePath().normalize().toString()
                    : "bytes:" + item.declaredPath();
            ImageId id = images.builder().getImageId(pathKey, item.fileName(), item.contents());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Processing image from build item: %s with preset %s", item, item.presetName());
            }
            processImage(staticResourceProducer, converter, images, null, item.declaredPath(), item.presetName(),
                    preset, new ResolvedSourceImage(item.declaredPath(), id, item.contents()), targetDir.path,
                    imageConfig);
        }

        return images;
    }

    /**
     * Resolve a preset by name; "default" falls back to the built-in default preset when not configured.
     *
     * @param context where the preset was referenced, for the error message
     */
    static PresetConfig resolvePreset(ImageConfig imageConfig, String presetName, String context) {
        if ("default".equals(presetName)) {
            return imageConfig.presets().getOrDefault(presetName, PresetConfig.DEFAULT);
        }
        PresetConfig preset = imageConfig.presets().get(presetName);
        if (preset == null) {
            throw new RuntimeException(
                    "Unknown preset '%s' in %s. Available presets (quarkus.qute.image.presets.*): %s"
                            .formatted(presetName, context, imageConfig.presets().keySet()));
        }
        return preset;
    }

    private void processGlobPatterns(ImageConfig imageConfig,
            List<ImagesDirBuildItem> imageSourceDirs, ProjectScannerBuildItem scanner,
            ImageConverter converter, BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            ImagesBuildItem images, Path targetDist) {
        List<ImageConfig.GeneratePattern> patterns = imageConfig.generate();
        for (ImageConfig.GeneratePattern pattern : patterns) {
            for (ImagesDirBuildItem dir : imageSourceDirs) {
                String scope = scannerScope(dir);
                List<ProjectFile> matched;
                try {
                    matched = scanner.query().scopeDir(scope).matchingGlob(pattern.glob()).list();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to scan for glob pattern: " + pattern.glob(), e);
                }
                for (ProjectFile file : matched) {
                    String declaredPath = "/" + file.scopedPath();
                    for (String presetName : pattern.presets()) {
                        PresetConfig preset = resolvePreset(imageConfig, presetName,
                                "generate pattern (glob: %s)".formatted(pattern.glob()));
                        ResolvedSourceImage resolved = toResolvedImage(images, file, declaredPath);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debugf("Glob match: %s with preset %s", file.scopedPath(), presetName);
                        }
                        processImage(staticResourceProducer, converter, images, null,
                                declaredPath, presetName, preset, resolved, targetDist, imageConfig);
                    }
                }
            }
        }
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void recordImages(ImageRecorder imageRecorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            ImagesBuildItem images) {
        Map<String, ImageTag> tags = images != null ? images.builder().computeImageTags() : Map.of();
        Map<String, Image> imageMap = images != null ? images.builder().computeImages() : Map.of();
        syntheticBeanProducer.produce(SyntheticBeanBuildItem.configure(Images.class)
                .supplier(imageRecorder.imagesSupplier(tags, imageMap))
                .named("images")
                .scope(Singleton.class)
                .unremovable()
                .done());
    }

    private static Map<String, ProjectFile> indexImageFiles(List<ImagesDirBuildItem> imageDirs,
            ProjectScannerBuildItem scanner) {
        Map<String, ProjectFile> result = new LinkedHashMap<>();
        for (ImagesDirBuildItem dir : imageDirs) {
            String scope = scannerScope(dir);
            try {
                for (ProjectFile file : scanner.query().scopeDir(scope).list()) {
                    result.putIfAbsent(ImageUtils.indexKey(dir.urlPrefix(), file.scopedPath()), file);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan image directory: " + dir, e);
            }
        }
        return result;
    }

    private static void collectImage(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            ImageConverter converter, Map<String, ProjectFile> indexedFiles, ImagesBuildItem images,
            ImageRef tag, Path templatePath, String templateName, Path targetDist, ImageConfig imageConfig) {

        ResolvedSourceImage resolvedImage;
        if (tag.fileParam().startsWith("/")) {
            resolvedImage = resolveFromIndex(images, indexedFiles, tag.fileParam());
        } else if (templatePath != null) {
            resolvedImage = resolveRelative(images, templatePath.getParent(), tag.fileParam());
        } else {
            throw new RuntimeException(
                    "Cannot resolve relative image path '%s' in template '%s'."
                            .formatted(tag.fileParam(), templateName)
                            + " Classpath templates (e.g. from themes) must use absolute image paths"
                            + " like '/images/photo.jpg'.");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf(" Found image tag for image: %s", tag.fileParam());
        }

        processImage(staticResourceProducer, converter, images, tag.templateId(),
                tag.fileParam(), tag.presetName(), tag.presetConfig(), resolvedImage, targetDist, imageConfig);
    }

    static void processImage(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            ImageConverter converter, ImagesBuildItem images, String templateId,
            String declaredPath, String presetName, PresetConfig preset,
            ResolvedSourceImage resolvedImage, Path targetDist, ImageConfig imageConfig) {

        ImagesBuilder.AddImageResult result = images.builder().addImage(resolvedImage);
        PresetConfig.Crop crop = preset.crop().orElse(null);
        String sourceExt = resolvedImage.id().extension();

        if (isPassThrough(imageConfig, sourceExt)) {
            result.image().passThrough(sourceExt);
            images.builder().scannedImageTag(templateId, declaredPath, presetName, preset, result.image());
            return;
        }

        ImageInfo info;
        try {
            info = converter.readInfo(resolvedImage.contents());
        } catch (UnsupportedFormatException e) {
            LOGGER.warnf("Image '%s' cannot be read by the '%s' converter (%s): it is used as is, without generated"
                    + " variants. For WebP sources add the quarkus-qute-web-image-vips extension (requires libvips).",
                    declaredPath, converter.id(), e.getMessage());
            result.image().passThrough(sourceExt);
            images.builder().scannedImageTag(templateId, declaredPath, presetName, preset, result.image());
            return;
        }
        result.image().info(new OriginalInfo(info.format(), info.width(), info.height()));

        images.builder().scannedImageTag(templateId, declaredPath, presetName, preset, result.image());

        for (String format : preset.normalizedFormats()) {
            String ext = ImageUtils.extensionFromFormat(format);
            if (!converter.supportsFormat(ext)) {
                LOGGER.warnf("Skipping format '%s': not supported by the active image converter."
                        + " For webp/avif support, add the quarkus-qute-web-image-vips extension (requires libvips).",
                        ext);
                continue;
            }
            for (int width : preset.widths()) {
                if (info.width() < width) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  Skipping width: %s (larger than source)", width);
                    }
                    continue;
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("  Generating width: %s format: %s", width, format);
                }
                int height = ImageSizing.computeTargetHeight(width, info.width(), info.height(),
                        crop != null ? crop.ratio() : null);
                result.image().addGeneratedImage(imageConfig.outputPath(),
                        new GeneratedImageOptions(width, height, format, crop, preset.quality(), converter.id()),
                        imageConfig.slugifyOutput(),
                        generatedImage -> {
                            Path outputPath = ImageUtils.generatedImagePath(targetDist, generatedImage);
                            if (isComplete(outputPath)) {
                                // the file name encodes the source digest and every setting, so it is current
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debugf("  Reusing %s", outputPath);
                                }
                            } else {
                                ImageOptions options = new ImageOptions(width, height, ext,
                                        crop != null
                                                ? new ImageOptions.CropOptions(crop.ratio(),
                                                        toCropPosition(crop.keep()))
                                                : null,
                                        preset.quality());
                                writeAtomically(converter, resolvedImage.contents(), options, outputPath);
                            }
                            staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                                    generatedImage.outputPath(), outputPath));
                        });
            }
        }
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
     * into place, so the build dir never holds a partially written variant under its final name.
     */
    static void writeAtomically(ImageConverter converter, byte[] contents, ImageOptions options,
            Path outputPath) {
        Path tmp = outputPath.resolveSibling("." + outputPath.getFileName());
        converter.process(contents, options, tmp);
        try {
            Files.move(tmp, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move generated image into place: " + outputPath, e);
        }
    }

    private static boolean isPassThrough(ImageConfig imageConfig, String extension) {
        for (String ext : imageConfig.passthrough()) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    private static ImageOptions.CropPosition toCropPosition(PresetConfig.Keep keep) {
        return switch (keep) {
            case BOTTOM -> ImageOptions.CropPosition.BOTTOM;
            case TOP -> ImageOptions.CropPosition.TOP;
            default -> ImageOptions.CropPosition.CENTER;
        };
    }

    private static Path resolveSourcePath(QuteImageTemplateToScanBuildItem template) {
        if (template == null || template.location == null) {
            return null;
        }
        return QuteImageScanProcessor.toLocalPath(template.location);
    }

    private static ResolvedSourceImage resolveFromIndex(ImagesBuildItem images,
            Map<String, ProjectFile> indexedFiles, String path) {
        ProjectFile file = indexedFiles.get(ImageUtils.indexKey(null, path));
        if (file == null) {
            throw new RuntimeException(
                    "Image does not exist or is not a file: " + path + " (looked up in indexed image directories)");
        }
        return toResolvedImage(images, file, path);
    }

    private static Path findRelativeImage(Path parentDir, String relativePath) {
        Path resolved = ImageUtils.resolveContained(parentDir, relativePath);
        if (resolved == null) {
            return null;
        }
        if (Files.isRegularFile(resolved)) {
            return resolved;
        }
        Path imagesBase = parentDir.resolve(ImageUtils.IMAGES_DIR);
        Path inImagesDir = ImageUtils.resolveContained(imagesBase, relativePath);
        if (inImagesDir != null && Files.isRegularFile(inImagesDir)) {
            return inImagesDir;
        }
        String fileName = Path.of(relativePath).getFileName().toString();
        Path prefixed = ImageUtils.resolveContained(parentDir, "_" + fileName);
        if (prefixed != null && Files.isRegularFile(prefixed)) {
            return prefixed;
        }
        return null;
    }

    private static ResolvedSourceImage resolveRelative(ImagesBuildItem images, Path parentDir, String relativePath) {
        Path normalizedParent = parentDir.normalize();
        Path resolved = findRelativeImage(normalizedParent, relativePath);
        if (resolved == null) {
            throw new RuntimeException("Image does not exist or is not a file: " + relativePath
                    + " (looked up at " + parentDir + ", also tried "
                    + ImageUtils.IMAGES_DIR + "/" + relativePath + " and _" + relativePath + ")");
        }
        try {
            byte[] contents = Files.readAllBytes(resolved);
            String name = resolved.getFileName().toString();
            String normalizedPath = resolved.toAbsolutePath().normalize().toString();
            ImageId id = images.builder().getImageId(normalizedPath, name, contents);
            return new ResolvedSourceImage(relativePath, id, contents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image: " + resolved, e);
        }
    }

    private static ResolvedSourceImage toResolvedImage(ImagesBuildItem images, ProjectFile file, String declaredPath) {
        byte[] contents = file.content();
        String name = file.scopedPath().contains("/")
                ? file.scopedPath().substring(file.scopedPath().lastIndexOf('/') + 1)
                : file.scopedPath();
        // local files are keyed by their absolute path so that other extensions can match them by file
        String pathKey = file.isLocalFile() ? file.file().toAbsolutePath().normalize().toString() : file.indexPath();
        ImageId id = images.builder().getImageId(pathKey, name, contents);
        return new ResolvedSourceImage(declaredPath, id, contents);
    }

    static String scannerScope(ImagesDirBuildItem dir) {
        if (dir.isLocal()) {
            return StringPaths.toUnixPath(dir.localPath().toString());
        }
        return dir.prefix();
    }
}
