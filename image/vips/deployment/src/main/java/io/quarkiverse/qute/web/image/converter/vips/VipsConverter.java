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
import io.quarkiverse.qute.web.image.converter.UnsupportedFormatException;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;

public class VipsConverter implements ImageConverter {

    @Override
    public String id() {
        return "vips";
    }

    @Override
    public ImageInfo readInfo(byte[] sourceImage) {
        final ImageInfo[] result = new ImageInfo[1];
        try {
            Vips.run(arena -> {
                VImage image = VImage.newFromBytes(arena, sourceImage);
                result[0] = new ImageInfo(detectFormat(sourceImage), image.getWidth(), image.getHeight(),
                        image.getFields().contains("n-pages") ? image.getInt("n-pages") : 1);
            });
        } catch (VipsError e) {
            throw new UnsupportedFormatException("libvips cannot read this image: " + e.getMessage());
        }
        return result[0];
    }

    @Override
    public void decode(byte[] sourceImage) {
        try {
            // the average forces every pixel through the loader
            Vips.run(arena -> VImage.newFromBytes(arena, sourceImage).avg());
        } catch (VipsError e) {
            throw new UnsupportedFormatException("libvips cannot decode this image: " + e.getMessage());
        }
    }

    @Override
    public void process(byte[] sourceImage, ImageOptions options, Path outputPath) {
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
    }

    private static VipsOption[] thumbnailOptions(ImageOptions options) {
        var opts = new ArrayList<VipsOption>();
        if (options.crop() != null) {
            opts.add(VipsOption.Int("height", options.height()));
            opts.add(VipsOption.Enum("crop", toCropInteresting(options.crop().keep())));
        }
        opts.add(VipsOption.Boolean("no-rotate", false));
        return opts.toArray(new VipsOption[0]);
    }

    private static VipsOption[] writeOptions(ImageOptions options) {
        var opts = new ArrayList<VipsOption>();
        if (options.quality() > 0) {
            opts.add(VipsOption.Int("Q", options.quality()));
        }
        // no EXIF, XMP, ICC or comments from the source: a remote one could carry anything, a photo carries GPS
        opts.add(VipsOption.Boolean("strip", true));
        return opts.toArray(new VipsOption[0]);
    }

    private static VipsInteresting toCropInteresting(PresetConfig.Keep keep) {
        return switch (keep) {
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
