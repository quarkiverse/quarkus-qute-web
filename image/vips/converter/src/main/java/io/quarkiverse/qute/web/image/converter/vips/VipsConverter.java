package io.quarkiverse.qute.web.image.converter.vips;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsError;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsInteresting;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.ImageOptions;

public class VipsConverter implements ImageConverter {

    @Override
    public ImageInfo readInfo(byte[] sourceImage) {
        final ImageInfo[] result = new ImageInfo[1];
        Vips.run(arena -> {
            VImage image = VImage.newFromBytes(arena, sourceImage);
            result[0] = new ImageInfo(detectFormat(sourceImage),
                    image.getWidth(), image.getHeight());
        });
        return result[0];
    }

    @Override
    public Path process(byte[] sourceImage, ImageOptions options, Path outputPath) {
        Vips.run(arena -> {
            try {
                VImage image = VImage.newFromBytes(arena, sourceImage);
                Files.createDirectories(outputPath.getParent());

                VImage resized = image.thumbnailImage(options.width(),
                        thumbnailOptions(options));
                resized.writeToFile(outputPath.toAbsolutePath().toString(),
                        writeOptions(options));
            } catch (IOException | VipsError e) {
                throw new RuntimeException("Failed to process image: " + outputPath, e);
            }
        });
        return outputPath;
    }

    private static VipsOption[] thumbnailOptions(ImageOptions options) {
        var opts = new ArrayList<VipsOption>();
        if (options.crop() != null) {
            opts.add(VipsOption.Int("height", options.height()));
            opts.add(VipsOption.Enum("crop", toCropInteresting(options.crop().position())));
        }
        opts.add(VipsOption.Boolean("no-rotate", false));
        return opts.toArray(new VipsOption[0]);
    }

    private static VipsOption[] writeOptions(ImageOptions options) {
        var opts = new ArrayList<VipsOption>();
        if (options.quality() > 0) {
            opts.add(VipsOption.Int("Q", options.quality()));
        }
        return opts.toArray(new VipsOption[0]);
    }

    private static VipsInteresting toCropInteresting(ImageOptions.CropPosition position) {
        return switch (position) {
            case CENTER -> VipsInteresting.INTERESTING_CENTRE;
            case TOP -> VipsInteresting.INTERESTING_HIGH;
            case BOTTOM -> VipsInteresting.INTERESTING_LOW;
        };
    }

    private static String detectFormat(byte[] data) {
        if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "png";
        }
        if (data.length >= 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            return "jpeg";
        }
        if (data.length >= 4 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return "webp";
        }
        return "unknown";
    }
}
