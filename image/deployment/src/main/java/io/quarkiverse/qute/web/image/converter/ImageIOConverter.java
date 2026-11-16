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

import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

public class ImageIOConverter implements ImageConverter {

    private static final Set<String> SUPPORTED_WRITE_FORMATS = Arrays.stream(ImageIO.getWriterFormatNames())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

    @Override
    public String id() {
        return "imageio";
    }

    @Override
    public boolean supportsFormat(String outputFormat) {
        return SUPPORTED_WRITE_FORMATS.contains(outputFormat.toLowerCase());
    }

    @Override
    public ImageInfo readInfo(byte[] sourceImage) {
        return withReader(sourceImage,
                reader -> new ImageInfo(reader.getFormatName(), reader.getWidth(0), reader.getHeight(0),
                        reader.getNumImages(true)));
    }

    @Override
    public void decode(byte[] sourceImage) {
        withReader(sourceImage, reader -> reader.read(0));
    }

    @Override
    public void process(byte[] sourceImage, ImageOptions options, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            BufferedImage source = withReader(sourceImage, reader -> reader.read(0));
            var builder = Thumbnails.of(source)
                    .size(options.width(), options.height())
                    .outputFormat(options.outputFormat())
                    .outputQuality(options.quality() / 100.0);
            if (options.crop() != null) {
                builder.crop(toPosition(options.crop().keep()));
            }
            builder.toFile(outputPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to process image", e);
        }
    }

    private static Positions toPosition(PresetConfig.Keep keep) {
        return switch (keep) {
            case CENTER -> Positions.CENTER;
            case TOP -> Positions.TOP_CENTER;
            case BOTTOM -> Positions.BOTTOM_CENTER;
        };
    }

    /** What the first reader accepting the bytes gives, or an {@link UnsupportedFormatException}. */
    private static <T> T withReader(byte[] sourceImage, Read<T> read) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(sourceImage))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input);
                    return read.apply(reader);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new UnsupportedFormatException("ImageIO cannot read this image: " + e.getMessage());
        }
        throw new UnsupportedFormatException("No ImageIO reader accepts this image");
    }

    private interface Read<T> {
        T apply(ImageReader reader) throws IOException;
    }
}
