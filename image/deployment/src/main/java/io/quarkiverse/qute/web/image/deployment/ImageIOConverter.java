package io.quarkiverse.qute.web.image.deployment;

import static io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem.addScaledImage;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.ImagesBuildItem;
import io.quarkiverse.qute.web.image.runtime.Images;

public class ImageIOConverter {
    private static final Logger LOGGER = Logger.getLogger(ImageIOConverter.class);

    public static void processImage(Images.ImageTag imageTag, ImagesBuildItem.ResolvedSourceImage resolvedImage,
            Images.Image processedImage,
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

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf(" Image has width: %s and format: %s, scaling to dimensions: %s", width, format,
                        imageTag.config().widths());
            }

            for (int dimension : imageTag.config().widths()) {
                if (width > dimension) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  [%s] Need to resize", dimension);
                    }
                    scaleImage(image, format, dimension,
                            processedImage, targetDist);
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
            Images.Image processedImage,
            Path targetDist)
            throws IOException {
        addScaledImage(processedImage, width, newVariant -> {
            try {
                Image scaledImage = image.getScaledInstance(width, -1, java.awt.Image.SCALE_DEFAULT);
                Path scaledAbsolutePath = resizedImagePath(targetDist, newVariant);
                // Make sure the target folder exists
                Files.createDirectories(scaledAbsolutePath.getParent());
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
                ImageIO.write(scaledImageRendered, format, scaledAbsolutePath.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
    }

    static Path resizedImagePath(Path targetPath,
            Images.Variant newVariant) {
        return targetPath.resolve(newVariant.path().substring(1));
    }

}
