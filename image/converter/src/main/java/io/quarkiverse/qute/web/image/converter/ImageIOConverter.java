package io.quarkiverse.qute.web.image.converter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

public class ImageIOConverter implements ImageConverter {

    private static final Set<String> SUPPORTED_WRITE_FORMATS = Arrays.stream(ImageIO.getWriterFormatNames())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

    @Override
    public boolean supportsFormat(String outputFormat) {
        return SUPPORTED_WRITE_FORMATS.contains(outputFormat.toLowerCase());
    }

    @Override
    public ImageInfo readInfo(byte[] sourceImage) {
        try {
            ReadResult result = readImage(sourceImage);
            return new ImageInfo(result.format(), result.image().getWidth(), result.image().getHeight());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image info", e);
        }
    }

    @Override
    public Path process(byte[] sourceImage, ImageOptions options, Path outputPath) {
        try {
            ReadResult result = readImage(sourceImage);
            Files.createDirectories(outputPath.getParent());
            var builder = Thumbnails.of(result.image())
                    .size(options.width(), options.height())
                    .outputFormat(options.outputFormat())
                    .outputQuality(options.quality() / 100.0);
            if (options.crop() != null) {
                builder.crop(toPosition(options.crop().position()));
            }
            builder.toFile(outputPath.toFile());
            return outputPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process image", e);
        }
    }

    private static Positions toPosition(ImageOptions.CropPosition position) {
        return switch (position) {
            case CENTER -> Positions.CENTER;
            case TOP -> Positions.TOP_CENTER;
            case BOTTOM -> Positions.BOTTOM_CENTER;
        };
    }

    private static ReadResult readImage(byte[] sourceImage) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO
                .createImageInputStream(new ByteArrayInputStream(sourceImage))) {
            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
            while (imageReaders.hasNext()) {
                ImageReader imageReader = imageReaders.next();
                try {
                    imageReader.setInput(imageInputStream);
                    return new ReadResult(imageReader.read(0), imageReader.getFormatName());
                } finally {
                    imageReader.dispose();
                }
            }
        }
        throw new IOException("Could not read image: unsupported format");
    }

    private record ReadResult(BufferedImage image, String format) {
    }
}
