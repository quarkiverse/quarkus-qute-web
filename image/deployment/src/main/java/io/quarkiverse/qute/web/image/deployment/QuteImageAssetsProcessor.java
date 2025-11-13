package io.quarkiverse.qute.web.image.deployment;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.runtime.ImageRecorder;
import io.quarkiverse.qute.web.image.runtime.ImageSectionHelperFactory;
import io.quarkiverse.qute.web.image.runtime.Images;
import io.quarkiverse.qute.web.image.runtime.Images.ImageId;
import io.quarkiverse.qute.web.image.runtime.Images.ResolvedSourceImage;
import io.quarkiverse.qute.web.image.spi.items.ImageSourceDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.WhitelistDirBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class QuteImageAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteImageAssetsProcessor.class);

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
    ImagesBuildItem initImages() {
        Images images = new Images();
        return new ImagesBuildItem(images);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void processTemplatesWithImages(ImageRecorder imageRecorder,
            BeanContainerBuildItem beanContainer,
            List<QuteImageTemplateToScanBuildItem> templateToScan,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            QuteImageTargetDirBuildItem targetDir,
            ImagesBuildItem imagesBuildItem,
            List<WhitelistDirBuildItem> whitelistDirs,
            List<ImageSourceDirBuildItem> imageSourceDirs) {
        if (templateToScan.isEmpty() || imagesBuildItem == null) {
            return;
        }
        // collect image usages, starting from the build-time templates
        Images images = imagesBuildItem.images;

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

        // now populate the runtime value from the build-time value
        for (var userEntry : images.collectImageUsers().entrySet()) {
            Images.Image image = userEntry.getKey();
            RuntimeValue<Images.Image> runtimeImage = imageRecorder.addImage(beanContainer.getValue(),
                    image.id,
                    image.collectVariants());
            for (Images.ImageUser user : userEntry.getValue()) {
                imageRecorder.addImageUser(beanContainer.getValue(), user.templateId, user.declaredPath, user.publicPath,
                        runtimeImage);
            }
        }
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

    private static void collectImage(ImageTagSection tag, String templateName, Path templatePath, Images images,
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
        Images.AddImageResult collectedImageResult = images.addImage(resolvedImage);
        images.registerImageUser(tag.section().getOrigin().getTemplateId(), tag.fileParam(), resolvedImage.id().publicPath(),
                collectedImageResult.image());
        if (collectedImageResult.created()) {
            processImage(resolvedImage, collectedImageResult.image(), staticResourceProducer, targetDist);
        }

    }

    private static ResolvedSourceImage resolveAbsoluteFile(Images images,
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
        throw new RuntimeException("Image does not exist or is not a file: " + relativePath + " (looked up at "
                + imageSourceDirs.stream().map(ImageSourceDirBuildItem::basePath).toList() + ")");
    }

    private static ResolvedSourceImage resolveImage(Predicate<Path> isWhitelist, Images images, Path resolvedPath, String name,
            Boolean isResource, String publicPath) {
        if (isResource) {
            // We split resources and filesystem for safety
            AtomicReference<ResolvedSourceImage> image = new AtomicReference<>();
            String resourcePath = resolvedPath.toString().startsWith("/") ? resolvedPath.toString().substring(1)
                    : resolvedPath.toString();
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

    private static void processImage(ResolvedSourceImage resolvedImage,
            Images.Image processedImage, BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            Path targetDist) {
        try {
            // FIXME: we should only read the image once to figure out its size, and later generate all collected variants
            BufferedImage image = null;
            String format = null;
            try (ImageInputStream imageInputStream = ImageIO
                    .createImageInputStream(new ByteArrayInputStream(resolvedImage.contents()))) {
                Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
                while (imageReaders.hasNext()) {
                    ImageReader imageReader = imageReaders.next();
                    try {
                        imageReader.setInput(imageInputStream);
                        // ignore if this throws: bad format
                        image = imageReader.read(0);
                        format = imageReader.getFormatName();
                        // if this worked, we found the image format
                        break;
                    } finally {
                        imageReader.dispose();
                    }
                }
            }
            int width = image.getWidth();

            if (!resolvedImage.served()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Serving original file: %s", resolvedImage.id().publicPath());
                }
                staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                        resolvedImage.id().publicPath(),
                        resolvedImage.contents()));
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf(" Image has width: %s and format: %s, scaling to dimensions: %s", width, format,
                        Arrays.toString(DIMENSIONS));
            }

            for (int dimension : DIMENSIONS) {
                if (width > dimension) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  [%s] Need to resize", dimension);
                    }
                    scaleImage(image, format, dimension,
                            processedImage, staticResourceProducer, targetDist);
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  [%s] No need to resize (source image is smaller)", dimension);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void scaleImage(BufferedImage image, String format, int width,
            Images.Image processedImage, BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            Path targetDist)
            throws IOException {
        processedImage.addScaledImage(width, newVariant -> {
            Image scaledImage = image.getScaledInstance(width, -1, java.awt.Image.SCALE_DEFAULT);
            Path scaledAbsolutePath = resizedImagePath(targetDist, newVariant);
            RenderedImage scaledImageRendered;
            if (scaledImage instanceof RenderedImage s) {
                scaledImageRendered = s;
            } else {
                BufferedImage buffered = new BufferedImage(scaledImage.getWidth(null), scaledImage.getHeight(null),
                        image.getType());
                Graphics graphics = buffered.getGraphics();
                graphics.drawImage(scaledImage, 0, 0, null);
                graphics.dispose();
                scaledImageRendered = buffered;
            }
            // FIXME: perhaps keeps this in memory?
            try {
                ImageIO.write(scaledImageRendered, format, scaledAbsolutePath.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String scaledWebPath = newVariant.path;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("   [%s] Resized to %s, accessible at: %s", width, scaledAbsolutePath, scaledWebPath);
            }
            staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                    scaledWebPath,
                    scaledAbsolutePath));
        });
    }

    private static String pathFromWebRoot(String resource, String root) {
        if (!resource.startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return resource.substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

    private static Path resizedImagePath(Path targetPath,
            Images.Image.Variant newVariant) {
        // Make sure the target folder exists
        Path targetProcessedImagesPath = targetPath.resolve(newVariant.path.substring(1));
        try {
            Files.createDirectories(targetProcessedImagesPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return targetProcessedImagesPath;
    }

}
