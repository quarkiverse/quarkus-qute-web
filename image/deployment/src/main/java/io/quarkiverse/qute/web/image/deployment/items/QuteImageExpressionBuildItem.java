package io.quarkiverse.qute.web.image.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * An image usage ({@code {#img src=...}} or {@code {img:path(...)}} / {@code {img:tag(...)}}) whose source is
 * not a string literal, so this extension cannot resolve it at build time by itself.
 * <p>
 * Other extensions that know the template data (a static site generator, for instance) can consume these
 * items, evaluate the expressions against their own model and feed the resulting files back through
 * {@link QuteImageToProcessBuildItem}.
 * <p>
 * A usage is reported once per template that renders it: the template it is written in, and every template
 * reaching it through {@code {#include}} or a user tag. {@link #templateId()} is the rendering template,
 * {@link #origin()} names the actual location when it differs.
 */
public final class QuteImageExpressionBuildItem extends MultiBuildItem {

    private final String templateId;
    private final String srcExpression;
    private final String preset;
    private final String origin;

    /**
     * @param templateId the Qute template id, as {@code Template.getId()} returns it at runtime (for instance
     *        {@code pub/expressions.html})
     * @param srcExpression the source expression as written in the template
     * @param preset the preset name: the literal written in the template, or {@code default} when absent or not
     *        a literal (the same rule as for literal sources)
     * @param origin a human readable origin such as {@code {#img} at line 12}
     */
    public QuteImageExpressionBuildItem(String templateId, String srcExpression, String preset, String origin) {
        this.templateId = templateId;
        this.srcExpression = srcExpression;
        this.preset = preset;
        this.origin = origin;
    }

    public String templateId() {
        return templateId;
    }

    public String srcExpression() {
        return srcExpression;
    }

    public String preset() {
        return preset;
    }

    public String origin() {
        return origin;
    }

    @Override
    public String toString() {
        return "QuteImageExpressionBuildItem{" + templateId + ": src=" + srcExpression + ", preset=" + preset
                + ", " + origin + "}";
    }
}
