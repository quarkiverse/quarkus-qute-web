package io.quarkiverse.qute.web.image.runtime.model;

import static io.quarkiverse.qute.web.image.runtime.ImageUtils.imageTagKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkiverse.qute.web.image.runtime.ResolvedPresetConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The processed tags and images. Recorded as a serialized resource rather than as bytecode constants, since a site
 * with thousands of images exceeds the constant pool of a recorded class; the classes of the graph are registered
 * for serialization in native.
 */
@Vetoed
@RegisterForReflection(serialization = true, targets = { Images.class, ImageTag.class, Image.class, ImageId.class,
        OriginalInfo.class, GeneratedImage.class, ResolvedPresetConfig.class, ResolvedPresetConfig.CropConfig.class,
        ResolvedPresetConfig.PixelRatioConfig.class, PresetConfig.Keep.class }, classNames = {
                // the JDK types in the graph: the maps, List.copyOf and Map.of (CollSer), config lists, boxed numbers
                "java.util.HashMap", "java.util.ArrayList", "java.util.CollSer",
                "java.util.Collections$UnmodifiableRandomAccessList", "java.util.Collections$UnmodifiableList",
                "java.util.Collections$UnmodifiableCollection", "java.lang.Number", "java.lang.Integer",
                "java.lang.Double" })
public class Images implements Serializable {

    public static final Images EMPTY = new Images(Map.of(), List.of());

    // the usages by templateId|declaredPath|preset, see ImageUtils.imageTagKey
    private final Map<String, ImageTag> tags;
    // quarkus.qute.suffixes: a template reloaded in dev mode reports its id without the suffix the build recorded
    private final List<String> suffixes;

    public Images(Map<String, ImageTag> tags, List<String> suffixes) {
        this.tags = tags;
        this.suffixes = suffixes;
    }

    public ImageTag get(String templateId, String declaredURI, String preset) {
        ImageTag tag = tags.get(imageTagKey(templateId, declaredURI, preset));
        for (int i = 0; tag == null && i < suffixes.size(); i++) {
            tag = tags.get(imageTagKey(templateId + "." + suffixes.get(i), declaredURI, preset));
        }
        if (tag == null) {
            tag = tags.get(imageTagKey(ImageUtils.GLOB_TEMPLATE_ID, declaredURI, preset));
        }
        return tag;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(this);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize the processed images", e);
        }
        return bytes.toByteArray();
    }

    public static Images read(InputStream in) {
        try (ObjectInputStream objects = new ObjectInputStream(in)) {
            return (Images) objects.readObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the processed images", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to read the processed images", e);
        }
    }

    /** Reads the resource the build recorded, from the context classloader. */
    public static Images read(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Processed images resource not found: " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the processed images", e);
        }
    }
}
