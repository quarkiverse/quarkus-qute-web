package io.quarkiverse.qute.web.image.dynamic.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.ImageOptions;
import io.quarkiverse.qute.web.image.converter.ImageSizing;
import io.quarkiverse.qute.web.image.runtime.DynamicImageResolver;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkiverse.qute.web.image.runtime.RuntimeImageListener;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.Image;
import io.quarkiverse.qute.web.image.runtime.model.ImageId;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.OriginalInfo;

@Singleton
public class DynamicImageResolverImpl implements DynamicImageResolver {

    private static final Logger LOG = Logger.getLogger(DynamicImageResolverImpl.class);

    private final DynamicImageRuntimeConfig config;
    private final RuntimeImageListener handler;
    private final ConcurrentHashMap<String, ImageTag> cache = new ConcurrentHashMap<>();

    @Inject
    public DynamicImageResolverImpl(DynamicImageRuntimeConfig config,
            Instance<RuntimeImageListener> handlerInstance) {
        this.config = config;
        this.handler = handlerInstance.isResolvable() ? handlerInstance.get() : null;
    }

    @Override
    public ImageTag resolve(String src, String preset) {
        String cacheKey = ImageUtils.imageTagKey(null, src, preset);
        ImageTag cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        byte[] sourceBytes = findImage(src);
        if (sourceBytes == null) {
            return null;
        }

        ResolvedPresetConfig presetConfig = config.presets().getOrDefault(preset,
                ResolvedPresetConfig.from(PresetConfig.DEFAULT));

        ImageConverter converter = config.converter();
        ImageInfo info = converter.readInfo(sourceBytes);
        String baseName = extractBaseName(src);
        String extension = extractExtension(src);
        String digest = ImageUtils.digest(sourceBytes);
        ImageId id = new ImageId(digest, baseName, extension);

        List<GeneratedImage> variants = new ArrayList<>();
        for (String format : presetConfig.formats()) {
            String normalizedFormat = ImageUtils.normalizeFormat(format);
            String ext = ImageUtils.extensionFromFormat(format);
            if (!converter.supportsFormat(ext)) {
                LOG.warnf("Skipping format '%s': not supported by the active image converter.", ext);
                continue;
            }

            List<Integer> widths = presetConfig.widths();
            if (widths == null || widths.isEmpty()) {
                widths = List.of(info.width());
            }

            for (int width : widths) {
                if (info.width() < width) {
                    continue;
                }
                int height = ImageSizing.computeTargetHeight(width, info.width(), info.height(), null);
                String settingsHash = ImageUtils.digest(
                        "%s-%s-%s".formatted(id.toString(), width, normalizedFormat));
                String outputPath = ImageUtils.computeOutputPath(id, width, settingsHash, ext,
                        config.slugifyOutput());

                Path targetFile = config.cacheDir().resolve(outputPath.substring(1));
                if (!Files.exists(targetFile)) {
                    ImageOptions options = new ImageOptions(width, height, ext, null, 100);
                    converter.process(sourceBytes, options, targetFile);
                    if (handler != null) {
                        handler.onImageConverted(outputPath, targetFile);
                    }
                }

                GeneratedImage generated = new GeneratedImage(id, width, height, normalizedFormat, ext,
                        settingsHash, outputPath);
                variants.add(generated);
            }
        }

        Image image = new Image(id, new OriginalInfo(info.format(), info.width(), info.height()), variants);
        ImageTag tag = new ImageTag(null, src, presetConfig, image);
        cache.put(cacheKey, tag);
        return tag;
    }

    public Path getCacheDir() {
        return config.cacheDir();
    }

    private byte[] findImage(String src) {
        String stripped = src.startsWith("/") ? src.substring(1) : src;
        for (ImageDir dir : config.imageDirs()) {
            if (dir.localPath() != null) {
                Path base = Path.of(dir.localPath());
                Path file = ImageUtils.resolveContained(base, stripped);
                if (file == null) {
                    LOG.warnf("Rejected image path outside allowed directory: %s", src);
                    continue;
                }
                if (Files.isRegularFile(file)) {
                    try {
                        return Files.readAllBytes(file);
                    } catch (IOException e) {
                        LOG.debugf("Failed to read local image: %s", file);
                    }
                }
            }
            String prefix = dir.prefix();
            String resourcePath = prefix.isEmpty() ? stripped : prefix + "/" + stripped;
            try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resourcePath)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (IOException e) {
                LOG.debugf("Failed to read classpath image: %s", resourcePath);
            }
        }
        return null;
    }

    private static String extractBaseName(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extractExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(dot + 1).toLowerCase() : "";
    }
}
