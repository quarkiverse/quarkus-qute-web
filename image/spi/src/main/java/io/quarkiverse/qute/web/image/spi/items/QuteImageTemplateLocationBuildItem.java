package io.quarkiverse.qute.web.image.spi.items;

import java.net.URI;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * The source a generated template comes from, so that relative image paths in that template
 * ({@code {#img 'photo.jpg'/}}) are resolved next to the real source rather than next to the generated file.
 * <p>
 * The source is the same URI the host would give to {@code TemplatePathBuildItem.Builder#source(URI)}: a
 * {@code file:} URI for a file on disk, or the URI of a classpath resource ({@code jar:...!/content/page.md} for
 * content packaged in a jar), in which case the images next to it are read from the classpath.
 *
 * @deprecated temporary. The channel for this is {@code TemplatePathBuildItem.Builder#source(URI)}, which the
 *             scanner honours, but Qute records the source URI of every template into one generated class and
 *             sites with thousands of pages overflow the JVM constant pool (quarkusio/quarkus#58089). Quarkus
 *             fixed it on main by writing the template data to a resource (quarkusio/quarkus#58090), not in the
 *             3.x releases. Until the host runs on a Quarkus with that fix, this item carries the same URI without
 *             going through the recorder. Once it does, set {@code source} on the {@code TemplatePathBuildItem}
 *             instead and this item can be removed.
 */
@Deprecated
public final class QuteImageTemplateLocationBuildItem extends MultiBuildItem {

    private final String templateId;
    private final URI source;

    /**
     * @param templateId the template path as given to the {@code TemplatePathBuildItem} (for instance
     *        {@code posts/hello/index.html})
     * @param source the URI of the template source, see the class javadoc
     */
    public QuteImageTemplateLocationBuildItem(String templateId, URI source) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.source = Objects.requireNonNull(source, "source");
    }

    public String templateId() {
        return templateId;
    }

    public URI source() {
        return source;
    }

    @Override
    public String toString() {
        return "QuteImageTemplateLocationBuildItem{" + templateId + " <- " + source + "}";
    }
}
