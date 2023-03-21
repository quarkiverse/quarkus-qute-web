package io.quarkiverse.quteserverpages.runtime;

import java.util.Optional;
import java.util.regex.Pattern;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class QspBuildTimeConfig {

    /**
     * The root path. All templates will be served relative to this path which is relative to the HTTP root path.
     *
     * If the template name ends with a suffix listed in the `quarkus.qute.suffixes` config property then the suffix may be
     * omitted.
     *
     * For example, a template located in `src/main/resource/templates/foo.html` will be served from the paths `/qsp/foo` and
     * `/qsp/foo.html` by default.
     *
     * @asciidoclet
     */
    @ConfigItem(defaultValue = "/qsp")
    public String rootPath;

    /**
     * This regular expression is used to hide template files from the `src/main/resource/templates` directory. Hidden templates
     * are not exposed.
     *
     * All template file paths are matched, including the versions without suffixes. The matched input is the file path relative
     * from the `templates` directory and the `/` is used as a path separator. For example, a template located in
     * `src/main/resource/templates/foo.html` will be matched for `foo.tml` and `foo`.
     *
     * By default, the user tags from the `src/main/resource/templates/tags` directory are hidden.
     *
     * @asciidoclet
     */
    @ConfigItem(defaultValue = "tags/.*")
    public Pattern hiddenTemplates;

    /**
     * The order of the qsp route which handles the templates.
     *
     * @asciidoclet
     */
    @ConfigItem
    public Optional<Integer> routeOrder;

    /**
     * If set to `true` then the qsp route should use a blocking handler.
     *
     * @asciidoclet
     */
    @ConfigItem(defaultValue = "true")
    public boolean useBlockingHandler;

}
