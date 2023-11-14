package io.quarkiverse.qute.web.runtime;

import java.util.Optional;
import java.util.regex.Pattern;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.web")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface QuteWebBuildTimeConfig {

    /**
     * The root path. All templates will be served relative to this path which is relative to the HTTP root path.
     *
     * If the template name ends with a suffix listed in the `quarkus.qute.suffixes` config property then the suffix may be
     * omitted.
     *
     * For example, a template located in `src/main/resource/templates/foo.html` will be served from the paths `/foo` and
     * `/foo.html` by default.
     *
     * @asciidoclet
     */
    @WithDefault("/")
    String rootPath();

    /**
     * The directory from which the templates are served. The path is relative to a template root directroy, i.e. relative to
     * `src/main/resource/templates` by default. For example, the value `ping` could be translated to
     * `src/main/resource/templates/ping`.
     *
     * By default, the templates located in the `src/main/resource/templates/pub` directory are served.
     *
     * @asciidoclet
     */
    @WithDefault("pub")
    String publicDir();

    /**
     * This regular expression is used to hide template files from the web templates path. Hidden templates are not exposed.
     *
     * All template file paths are matched, including the versions without suffixes. The matched input is the file path relative
     * from the web templates path (for example `templates/web`) and the `/` is used as a path separator. For example, a
     * template located in `src/main/resource/templates/web/foo.html` will be matched for `foo.tml` and `foo`.
     *
     * By default, no templates are hidden.
     *
     * @asciidoclet
     */
    Optional<Pattern> hiddenTemplates();

    /**
     * The order of the route which handles the templates.
     *
     * By default, the route is executed before the default routes (static resources, etc.).
     *
     * @asciidoclet
     */
    @WithDefault("1000")
    int routeOrder();

    /**
     * If set to `true` then the route should use a blocking handler.
     *
     * @asciidoclet
     */
    @WithDefault("true")
    boolean useBlockingHandler();

}
