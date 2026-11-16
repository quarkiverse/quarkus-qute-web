package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageIOConverter;
import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageProcessedBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageNamespaceResolver;
import io.quarkiverse.qute.web.image.runtime.ImageRecorder;
import io.quarkiverse.qute.web.image.runtime.ImageTagFactory;
import io.quarkiverse.qute.web.image.runtime.ImageTemplateExtension;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;
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
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.deployment.TemplatePathExcludeBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class QuteImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteImageProcessor.class);

    /** The processed images are recorded as a serialized resource: see {@link Images}. */
    static final String IMAGES_RESOURCE = "META-INF/qute-image/images.ser";

    @BuildStep
    void initBeans(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageTagFactory.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageNamespaceResolver.class));
        // the extension methods are discovered on the bean archive index
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageTemplateExtension.class));
    }

    /**
     * This extension owns the {@code {#img}} tag (see {@link ImageTagFactory}). Qute registers user tags after the
     * section helpers under the same name, so a user tag named {@code img} (a fallback shipped by a host for
     * applications without this extension, for instance) would shadow it: it is excluded instead.
     */
    @BuildStep
    TemplatePathExcludeBuildItem excludeUserTagNamedImg() {
        return new TemplatePathExcludeBuildItem("tags/img\\.html");
    }

    /** Quarkus's own static root: an image at META-INF/resources/images/a.jpg is {@code /images/a.jpg}. */
    @BuildStep
    ImagesDirBuildItem defaultImagesDir() {
        return ImagesDirBuildItem.of("META-INF/resources");
    }

    @BuildStep
    void wireScannerItems(
            List<ImagesDirBuildItem> imageDirs,
            ProjectRootBuildItem projectRoot,
            BuildProducer<ScanDeclarationBuildItem> declarationProducer,
            BuildProducer<ScanLocalDirBuildItem> localDirProducer) {
        for (ImagesDirBuildItem dir : imageDirs) {
            String scope = scannerScope(dir, projectRoot);
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
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            QuteImageTargetDirBuildItem targetDir,
            List<ImagesDirBuildItem> imageSourceDirs,
            ProjectScannerBuildItem scanner,
            ProjectRootBuildItem projectRoot,
            HttpRootPathBuildItem rootPath) {
        String outputPath = imageConfig.outputPath();
        if (!outputPath.startsWith("/") || outputPath.endsWith("/")) {
            throw new IllegalArgumentException(
                    "quarkus.qute.image.output-path must start with '/' and not end with '/', got: " + outputPath);
        }
        boolean hasGlobPatterns = !imageConfig.generate().isEmpty();
        if (templateToScan.isEmpty() && !hasGlobPatterns && toProcess.isEmpty()) {
            return null;
        }
        LOGGER.infof("Converting images (build dir %s)", displayPath(projectRoot, targetDir.path));
        ImagesBuildItem images = new ImagesBuildItem(urlPrefix(rootPath));
        ImageConverter converter = converterItem.map(ImageConverterBuildItem::get).orElse(new ImageIOConverter());
        ImageConversion conversion = new ImageConversion(imageConfig, converter, images, targetDir.path,
                indexImageFiles(imageSourceDirs, scanner, projectRoot), staticResourceProducer);

        for (QuteImageTemplateToScanBuildItem template : templateToScan) {
            LOGGER.debugf("Inspecting (run-time) template %s for image tags", template.id());
            Path templatePath = resolveSourcePath(template);
            String resourceDir = templatePath == null ? resolveResourceDir(template) : null;
            for (ImageRef tag : template.imageRefs()) {
                conversion.collectImage(tag, templatePath, resourceDir, template.id());
            }
        }
        if (hasGlobPatterns) {
            processGlobPatterns(imageConfig, imageSourceDirs, scanner, projectRoot, conversion);
        }
        for (QuteImageToProcessBuildItem item : toProcess) {
            conversion.processItem(item);
        }
        // dev mode: a changed source gets its variants regenerated
        for (String path : conversion.watchedSources()) {
            watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(path));
        }

        LOGGER.info(images.stats().summary(images.builder().size()));
        return images;
    }

    /** A path relative to the project root when under it, for the logs. */
    static String displayPath(ProjectRootBuildItem root, Path path) {
        return root.exists() && path.startsWith(root.path()) ? root.path().relativize(path).toString() : path.toString();
    }

    /** The HTTP root path as a url prefix: without its trailing slash, empty at the root. */
    static String urlPrefix(HttpRootPathBuildItem rootPath) {
        String root = rootPath.getRootPath();
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }

    private static void processGlobPatterns(ImageConfig imageConfig, List<ImagesDirBuildItem> imageSourceDirs,
            ProjectScannerBuildItem scanner, ProjectRootBuildItem projectRoot, ImageConversion conversion) {
        for (ImageConfig.GeneratePattern pattern : imageConfig.generate()) {
            // the same url may come from two dirs (a classpath and a local view of one dir): first wins, as in the index
            Set<String> declared = new HashSet<>();
            for (ImagesDirBuildItem dir : imageSourceDirs) {
                String scope = scannerScope(dir, projectRoot);
                List<ProjectFile> matched;
                try {
                    matched = scanner.query().scopeDir(scope).matchingGlob(pattern.glob()).list();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to scan for glob pattern: " + pattern.glob(), e);
                }
                for (ProjectFile file : matched) {
                    // the url of the file, the key a template lookup uses
                    String declaredPath = StringPaths
                            .prefixWithSlash(ImageUtils.indexKey(dir.urlPrefix(), file.scopedPath()));
                    if (!declared.add(declaredPath)) {
                        continue;
                    }
                    for (String presetName : pattern.presets()) {
                        conversion.processGlobMatch(file, declaredPath, presetName, pattern.glob());
                    }
                }
            }
        }
    }

    /** The finished outcome of the processing, for the recorder and for other extensions. */
    @BuildStep
    QuteImageProcessedBuildItem processedImages(ImagesBuildItem images) {
        if (images == null) {
            return null;
        }
        return new QuteImageProcessedBuildItem(images.builder().computeImageTags(images.urlPrefix()));
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void recordImages(ImageRecorder recorder, Optional<QuteImageProcessedBuildItem> processed, QuteConfig quteConfig,
            BuildProducer<GeneratedResourceBuildItem> resources, BuildProducer<SyntheticBeanBuildItem> beans) {
        Images images = processed.map(p -> new Images(p.tagsByKey(), quteConfig.suffixes())).orElse(Images.EMPTY);
        resources.produce(new GeneratedResourceBuildItem(IMAGES_RESOURCE, images.toBytes()));
        beans.produce(SyntheticBeanBuildItem.configure(Images.class).supplier(recorder.imagesSupplier(IMAGES_RESOURCE))
                .scope(Singleton.class).unremovable().done());
    }

    /** The serialized resource, for native (its classes are registered on {@link Images}). */
    @BuildStep
    NativeImageResourceBuildItem registerImagesResourceForNative() {
        return new NativeImageResourceBuildItem(IMAGES_RESOURCE);
    }

    private static Map<String, ProjectFile> indexImageFiles(List<ImagesDirBuildItem> imageDirs,
            ProjectScannerBuildItem scanner, ProjectRootBuildItem projectRoot) {
        Map<String, ProjectFile> result = new LinkedHashMap<>();
        for (ImagesDirBuildItem dir : imageDirs) {
            String scope = scannerScope(dir, projectRoot);
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

    private static Path resolveSourcePath(QuteImageTemplateToScanBuildItem template) {
        return template.location() == null ? null : QuteImageScanProcessor.toLocalPath(template.location());
    }

    /**
     * The classpath directory of a template whose source is a classpath resource (a {@code jar:} URI), or null.
     */
    private static String resolveResourceDir(QuteImageTemplateToScanBuildItem template) {
        String resourcePath = QuteImageScanProcessor.resourcePath(template.location());
        if (resourcePath == null) {
            return null;
        }
        int slash = resourcePath.lastIndexOf('/');
        return slash < 0 ? "" : resourcePath.substring(0, slash);
    }

    /** The scanner indexes a local dir relative to the project root, so its scope is that relative path. */
    static String scannerScope(ImagesDirBuildItem dir, ProjectRootBuildItem root) {
        if (!dir.isLocal()) {
            return dir.prefix();
        }
        if (!root.exists()) {
            throw new IllegalStateException(
                    "Image directory %s needs a project root, none was found".formatted(dir.localPath()));
        }
        Path local = root.path().resolve(dir.localPath()).normalize();
        return StringPaths.toUnixPath(root.path().relativize(local).toString());
    }
}
