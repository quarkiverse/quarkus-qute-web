package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.deployment.items.model.GeneratedImageOptions;
import io.quarkiverse.qute.web.image.deployment.items.model.ImagesBuilder;
import io.quarkiverse.qute.web.image.deployment.items.model.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
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
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            QuteImageTargetDirBuildItem targetDir,
            List<ImagesDirBuildItem> imageSourceDirs,
            ProjectScannerBuildItem scanner) {
        boolean hasGlobPatterns = !imageConfig.generate().isEmpty();
        if (templateToScan.isEmpty() && !hasGlobPatterns) {
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
            for (ImageTagSection tag : template.sectionNodes) {
                collectImage(staticResourceProducer, converter, indexedFiles, images, tag,
                        templatePath, template.id, targetDir.path, imageConfig.slugifyOutput());
            }
        }

        if (hasGlobPatterns) {
            processGlobPatterns(imageConfig, imageSourceDirs, scanner, converter,
                    staticResourceProducer, images, targetDir.path);
        }

        return images;
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
                        PresetConfig preset;
                        if ("default".equals(presetName)) {
                            preset = imageConfig.presets().getOrDefault(presetName, PresetConfig.DEFAULT);
                        } else {
                            preset = imageConfig.presets().get(presetName);
                            if (preset == null) {
                                throw new RuntimeException(
                                        "Unknown preset '%s' in generate pattern (glob: %s). Available presets: %s"
                                                .formatted(presetName, pattern.glob(),
                                                        imageConfig.presets().keySet()));
                            }
                        }
                        ResolvedSourceImage resolved = toResolvedImage(images, file, declaredPath);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debugf("Glob match: %s with preset %s", file.scopedPath(), presetName);
                        }
                        processImage(staticResourceProducer, converter, images, null,
                                declaredPath, presetName, preset, resolved, targetDist,
                                imageConfig.slugifyOutput());
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
                    String key = dir.urlPrefix() != null && !dir.urlPrefix().isEmpty()
                            ? dir.urlPrefix() + "/" + file.scopedPath()
                            : file.scopedPath();
                    result.putIfAbsent(key, file);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan image directory: " + dir, e);
            }
        }
        return result;
    }

    private static void collectImage(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            ImageConverter converter, Map<String, ProjectFile> indexedFiles, ImagesBuildItem images,
            ImageTagSection tag, Path templatePath, String templateName, Path targetDist, boolean slugifyOutput) {

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

        processImage(staticResourceProducer, converter, images, tag.section().getOrigin().getTemplateId(),
                tag.fileParam(), tag.presetName(), tag.presetConfig(), resolvedImage, targetDist, slugifyOutput);
    }

    static void processImage(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            ImageConverter converter, ImagesBuildItem images, String templateId,
            String declaredPath, String presetName, PresetConfig preset,
            ResolvedSourceImage resolvedImage, Path targetDist, boolean slugifyOutput) {

        ImagesBuilder.AddImageResult result = images.builder().addImage(resolvedImage);
        PresetConfig.Crop crop = preset.crop().orElse(null);

        ImageInfo info = converter.readInfo(resolvedImage.contents());
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
                result.image().addGeneratedImage(
                        new GeneratedImageOptions(width, height, format, crop, preset.quality()), slugifyOutput,
                        generatedImage -> {
                            Path outputPath = ImageUtils.generatedImagePath(targetDist, generatedImage);
                            ImageOptions options = new ImageOptions(width, height, ext,
                                    crop != null
                                            ? new ImageOptions.CropOptions(crop.ratio(), toCropPosition(crop.keep()))
                                            : null,
                                    preset.quality());
                            converter.process(resolvedImage.contents(), options, outputPath);
                            staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                                    generatedImage.outputPath(), outputPath));
                        });
            }
        }
    }

    private static ImageOptions.CropPosition toCropPosition(PresetConfig.Keep keep) {
        return switch (keep) {
            case LOW -> ImageOptions.CropPosition.BOTTOM;
            case HIGH -> ImageOptions.CropPosition.TOP;
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
        String stripped = path.startsWith("/") ? path.substring(1) : path;
        ProjectFile file = indexedFiles.get(stripped);
        if (file == null) {
            throw new RuntimeException(
                    "Image does not exist or is not a file: " + stripped + " (looked up in indexed image directories)");
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
            String normalizedPath = resolved.toAbsolutePath().toString();
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
        ImageId id = images.builder().getImageId(file.indexPath(), name, contents);
        return new ResolvedSourceImage(declaredPath, id, contents);
    }

    static String scannerScope(ImagesDirBuildItem dir) {
        if (dir.isLocal()) {
            return StringPaths.toUnixPath(dir.localPath().toString());
        }
        return dir.prefix();
    }
}
