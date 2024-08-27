package io.quarkiverse.qute.web.deployment;

import static io.quarkiverse.qute.web.runtime.PathUtils.removeExtension;
import static io.quarkiverse.qute.web.runtime.PathUtils.removeLeadingSlash;
import static io.quarkiverse.qute.web.runtime.PathUtils.removeTrailingSlash;

import io.quarkus.builder.item.MultiBuildItem;

public final class QuteWebTemplateBuildItem extends MultiBuildItem {

    /**
     * templatePath is used also as path if link is null (e.g. "my-blog-post")
     */
    private final String templatePath;

    /**
     * The link to use for this template or null to use the template path (e.g "posts/my-blog-post")
     *
     * If two links are identical, an exception is thrown
     * If a link and a path are identical for different items, the link has priority
     */
    private final String link;

    public QuteWebTemplateBuildItem(String templatePath, String link) {
        this.templatePath = removeExtension(templatePath);
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

    private static String normalizeLink(String link) {
        if (link == null) {
            return null;
        }
        return removeTrailingSlash(removeLeadingSlash(link));
    }

}
