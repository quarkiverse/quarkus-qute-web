package io.quarkiverse.qute.web.image.deployment;

import static io.quarkiverse.qute.web.image.deployment.ImageIOConverter.processImage;
import static io.quarkiverse.qute.web.image.deployment.ImageIOConverter.resizedImagePath;
import static io.quarkiverse.qute.web.image.deployment.QuteImageScanProcessor.toUnixPath;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem.AddImageResult;
import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageRecorder;
import io.quarkiverse.qute.web.image.runtime.ImageSectionHelperFactory;
import io.quarkiverse.qute.web.image.runtime.Images;
import io.quarkiverse.qute.web.image.runtime.Images.ImageId;
import io.quarkiverse.qute.web.image.spi.items.ImageSourceDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.WhitelistDirBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class QuteImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteImageProcessor.class);

    // From https://dev.to/razbakov/responsive-images-best-practices-in-2025-4dlb
    private static final int[] DIMENSIONS = new int[] {
            640,
            1024,
            1920,
            2560,
    };

    @BuildStep
    void initBundleBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(Images.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageSectionHelperFactory.class));
    }

    @BuildStep
    ImagesBuildItem processTemplatesWithImages(
            ImageConfig imageConfig,
            List<QuteImageTemplateToScanBuildItem> templateToScan,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            QuteImageTargetDirBuildItem targetDir,
            List<WhitelistDirBuildItem> whitelistDirs,
            List<ImageSourceDirBuildItem> imageSourceDirs) {
        if (templateToScan.isEmpty()) {
            return null;
        }
        ImagesBuildItem images = new ImagesBuildItem();

        for (QuteImageTemplateToScanBuildItem template : templateToScan) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Inspecting (run-time) template %s for image tags",
                        template.id);
            }
            // default to null
            Path templatePath = resolveSourcePath(template);
            // we don't want to place images in the templates folder
            for (ImageTagSection resp : template.sectionNodes) {
                collectImage(resp,
                        template.id,
                        templatePath,
                        images,
                        staticResourceProducer,
                        targetDir.path,
                        whitelistPredicate(whitelistDirs),
                        imageSourceDirs);
            }

        }
        return images;
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void recordImages(ImageRecorder imageRecorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            ImagesBuildItem images) {

        if (images == null) {
            return;
        }

        syntheticBeanProducer.produce(SyntheticBeanBuildItem.configure(Images.class)
                .supplier(imageRecorder.imagesSupplier(images.tags()))
                .named("images")
                .scope(Singleton.class)
                .unremovable()
                .done());

    }

    private static Predicate<Path> whitelistPredicate(final List<WhitelistDirBuildItem> whitelistDirs) {
        return (p) -> {
            for (WhitelistDirBuildItem whitelistDir : whitelistDirs) {
                if (p.normalize().toAbsolutePath().startsWith(whitelistDir.path().toAbsolutePath())) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Path resolveSourcePath(QuteImageTemplateToScanBuildItem template) {
        if (template == null || template.location == null) {
            return null;
        }
        if (template.location.getScheme() == null) {
            URI baseUri = Paths.get("").toAbsolutePath().toUri();
            return Paths.get(baseUri.resolve(template.location));
        }
        if ("file".equalsIgnoreCase(template.location.getScheme()))
            return Paths.get(template.location);
        return null;
    }

    private static void collectImage(ImageTagSection tag, String templateName, Path templatePath, ImagesBuildItem images,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, Path targetDist,
            Predicate<Path> whitelistPredicate, List<ImageSourceDirBuildItem> imageSourceDirs) {

        Path imagePath = Path.of(tag.fileParam());
        ResolvedSourceImage resolvedImage;
        // We can't use Path.isAbsolute on Windows, and our paths are expected to be URIs anyways
        if (tag.fileParam().startsWith("/")) {
            // we need to resolve by passing a string, otherwise we get an exception due to different FS providers
            // for zip filesystems
            resolvedImage = resolveAbsoluteFile(images, whitelistPredicate, imageSourceDirs,
                    tag.fileParam());
        } else if (templatePath != null) {
            Path resolvedPath = templatePath.getParent().resolve(imagePath).normalize();
            // currently we know the path is on the local fs, we might add support for relative resources in the future
            // all build dirs are whitelisted
            final String name = resolvedPath.getFileName().toString();
            resolvedImage = resolveImage(whitelistPredicate, images, resolvedPath, name, false, null);
            if (resolvedImage == null) {
                throw new RuntimeException("Image does not exist or is not a file: " + imagePath + " (looked up at "
                        + resolvedPath + ")");
            }
        } else {
            throw new RuntimeException(
                    "Cannot refer to relative files from template when we do not know the template path: "
                            + templateName);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf(" Found image tag for image: %s", imagePath);
        }
        AddImageResult collectedImageResult = images.addImage(resolvedImage);
        final Images.ImageTag imageTag = images.registerImageTag(tag.section().getOrigin().getTemplateId(), tag.fileParam(),
                resolvedImage.id().publicPath(), tag.presetConfig(),
                collectedImageResult.image());
        if (collectedImageResult.created()) {
            if (!resolvedImage.served()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Serving original file: %s", resolvedImage.id().publicPath());
                }
                staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                        resolvedImage.id().publicPath(),
                        resolvedImage.contents()));
            }

            processImage(imageTag, resolvedImage, collectedImageResult.image(), targetDist);
            for (Map.Entry<Integer, Images.Variant> e : collectedImageResult.image().variants().entrySet()) {
                Path scaledAbsolutePath = resizedImagePath(targetDist, e.getValue());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("   [%s] Resized to %s, accessible at: %s", e.getValue().width(), scaledAbsolutePath,
                            e.getValue().path());
                }
                staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                        e.getValue().path(),
                        scaledAbsolutePath));
            }
        }

    }

    private static ResolvedSourceImage resolveAbsoluteFile(ImagesBuildItem images,
            Predicate<Path> whitelistPredicate,
            List<ImageSourceDirBuildItem> imageSourceDirs,
            String absolutePath) {
        final String relativePath = absolutePath.substring(1);
        for (ImageSourceDirBuildItem imageSourceDir : imageSourceDirs) {
            Path resolvedPath = imageSourceDir.basePath().resolve(relativePath).normalize();
            final String publicPath = imageSourceDir.toPublicPath(absolutePath);
            ResolvedSourceImage ret = resolveImage(whitelistPredicate, images, resolvedPath,
                    Path.of(publicPath).getFileName().toString(), imageSourceDir.isResource(),
                    imageSourceDir.isServed() ? publicPath : null);
            if (ret != null) {
                return ret;
            }
        }
        final List<String> sources = imageSourceDirs.stream().map(ImageSourceDirBuildItem::basePath)
                .map(QuteImageScanProcessor::toUnixPath).toList();
        throw new RuntimeException(
                "Image does not exist or is not a file: " + relativePath + " (looked up at " + sources + ")");
    }

    private static ResolvedSourceImage resolveImage(Predicate<Path> isWhitelist,
            ImagesBuildItem images,
            Path resolvedPath,
            String name,
            Boolean isResource,
            String publicPath) {
        // We split resources and filesystem in different cases for safety
        if (isResource) {
            AtomicReference<ResolvedSourceImage> image = new AtomicReference<>();
            final String unixPath = toUnixPath(resolvedPath);
            String resourcePath = unixPath.startsWith("/") ? unixPath.substring(1)
                    : unixPath;
            QuarkusClassLoader.visitRuntimeResources(resourcePath, c -> {
                try {
                    final byte[] contents = Files.readAllBytes(c.getPath());
                    ImageId id = images.getImageId(c.getPath().toAbsolutePath(), name, contents, publicPath);
                    image.set(new ResolvedSourceImage(resolvedPath, id, publicPath != null, contents));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read image " + resolvedPath + " from the classpath", e);
                }
            });
            if (image.get() != null) {
                return image.get();
            }
        } else {
            final Path absolutePath = resolvedPath.toAbsolutePath();
            if (!isWhitelist.test(absolutePath)) {
                throw new RuntimeException("Image path outside whitelist directories: " + resolvedPath + " ");
            }
            if (Files.isRegularFile(absolutePath)) {
                try {
                    final byte[] contents = Files.readAllBytes(absolutePath);
                    ImageId id = images.getImageId(absolutePath, name, contents, publicPath);
                    return new ResolvedSourceImage(absolutePath, id, publicPath != null, contents);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read image " + resolvedPath + " from the filesystem", e);
                }
            }
        }
        return null;
    }

    private static String pathFromWebRoot(String resource, String root) {
        if (!resource.startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return resource.substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

}
