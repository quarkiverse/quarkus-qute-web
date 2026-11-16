package io.quarkiverse.qute.web.image.deployment.items;

import java.net.URI;
import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A template and the literal image sources it uses, to convert at build time.
 *
 * @param location where relative sources resolve from: the template file, or a {@code jar:} resource; null when
 *        unknown (relative sources then fail)
 */
public final class QuteImageTemplateToScanBuildItem extends MultiBuildItem {

    private final URI location;
    private final List<ImageRef> imageRefs;
    private final String id;

    public QuteImageTemplateToScanBuildItem(URI location, List<ImageRef> imageRefs, String id) {
        this.location = location;
        this.imageRefs = List.copyOf(imageRefs);
        this.id = id;
    }

    public URI location() {
        return location;
    }

    public List<ImageRef> imageRefs() {
        return imageRefs;
    }

    /** The template id, the key the usages are looked up under at render time. */
    public String id() {
        return id;
    }

    /**
     * A literal usage: the source as written, the preset name, and where it was written for the error messages
     * (for instance {@code {#img}} or {@code {img:path(...)}}).
     */
    public record ImageRef(String fileParam, String presetName, String context) {
    }
}
