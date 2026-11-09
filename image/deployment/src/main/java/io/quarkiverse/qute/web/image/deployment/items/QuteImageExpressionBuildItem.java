package io.quarkiverse.qute.web.image.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * An image usage ({@code {#img src=...}} or {@code {img:path(...)}} / {@code {img:tag(...)}}) whose source is
 * not a string literal, so this extension cannot resolve it at build time by itself.
 * <p>
 * Other extensions that know the template data (a static site generator, for instance) can consume these
 * items, evaluate the expressions against their own model and feed the resulting files back through
 * {@link QuteImageToProcessBuildItem}.
 */
public final class QuteImageExpressionBuildItem extends MultiBuildItem {

    private final String templateId;
    private final String srcExpression;
    private final String presetExpression;
    private final String rootTypeInfo;
    private final String origin;

    /**
     * @param templateId the Qute template id, as {@code Template.getId()} returns it at runtime (for instance
     *        {@code pub/expressions.html})
     * @param srcExpression the source expression as written in the template
     * @param presetExpression the preset expression as written, or the literal {@code 'default'} when absent
     * @param rootTypeInfo the type info of the first part of the source expression, may be null
     * @param origin a human readable origin such as {@code {#img} at line 12}
     */
    public QuteImageExpressionBuildItem(String templateId, String srcExpression, String presetExpression,
            String rootTypeInfo, String origin) {
        this.templateId = templateId;
        this.srcExpression = srcExpression;
        this.presetExpression = presetExpression;
        this.rootTypeInfo = rootTypeInfo;
        this.origin = origin;
    }

    public String templateId() {
        return templateId;
    }

    public String srcExpression() {
        return srcExpression;
    }

    public String presetExpression() {
        return presetExpression;
    }

    public String rootTypeInfo() {
        return rootTypeInfo;
    }

    public String origin() {
        return origin;
    }

    @Override
    public String toString() {
        return "QuteImageExpressionBuildItem{" + templateId + ": src=" + srcExpression + ", preset=" + presetExpression
                + ", " + origin + "}";
    }
}
