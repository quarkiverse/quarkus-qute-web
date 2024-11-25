package io.quarkiverse.qute.web.deployment;

import static io.quarkiverse.qute.web.runtime.PathUtils.removeLeadingSlash;
import static io.quarkiverse.qute.web.runtime.PathUtils.removeTrailingSlash;

import io.quarkiverse.qute.web.runtime.PathUtils;
import io.quarkiverse.qute.web.runtime.QuteWebBuildTimeConfig;
import io.quarkus.builder.item.MultiBuildItem;

public final class QuteWebTemplateBuildItem extends MultiBuildItem {

    /**
     * The path relative to the template root, e.g. "my-blog-post.html". It is used also as path if link is {@code null}.
     */
    private final String templatePath;

    /**
     * The link to use for this template (e.g "posts/my-blog-post") or {@code null} to use the template path.
     * <p>
     * If two links are identical, an exception is thrown.
     * <p>
     * If a link and a path are identical for different items, the link has priority.
     */
    private final String link;

    public QuteWebTemplateBuildItem(String templatePath, String link) {
        this.templatePath = templatePath;
        this.link = normalizeLink(link);
    }

    public String templatePath() {
        return templatePath;
    }

    public String link() {
        return link;
    }

    public boolean hasLink() {
        return link != null;
    }

    public String getPagePath(QuteWebBuildTimeConfig config) {
        // If no link is set we need to remove the public dir from the template path: /pub/persons -> persons
        return link != null ? link : PathUtils.removeLeadingSlash(templatePath.replace(config.publicDir(), ""));
    }

    private static String normalizeLink(String link) {
        if (link == null) {
            return null;
        }
        return removeTrailingSlash(removeLeadingSlash(link));
    }

}
