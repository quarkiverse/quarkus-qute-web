///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.qute:qute-core:3.35.1
//DEPS org.yaml:snakeyaml:2.4
//JAVA 21

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.TemplateException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * POC: can we pre-resolve non-literal {#img src=...} expressions at build time
 * using a light page model + Qute core evaluation?
 *
 * Usage: jbang ImagePoc.java /path/to/quarkus-roq
 */
public class ImagePoc {

    // ---------- extraction ----------
    record Usage(String templateId, String kind, String srcExpr, boolean literal, String preset, String raw) {
    }

    static final Pattern IMG_TAG = Pattern.compile("\\{#img\\s+([^}]*?)/?\\}");
    static final Pattern IMG_NS = Pattern.compile("\\{img:(?:url|tag)\\(([^,)]+)(?:,\\s*(?:\"([^\"]*)\"|'([^']*)'))?\\)\\}");
    static final Pattern PARAM = Pattern.compile("(\\w+)=('([^']*)'|\"([^\"]*)\"|[^\\s]+)|('[^']*'|\"[^\"]*\")");

    static List<Usage> extract(Path root) throws IOException {
        List<Usage> usages = new ArrayList<>();
        List<Path> dirs = List.of(
                root.resolve("blog/templates"),
                root.resolve("blog/content"),
                root.resolve("roq-theme/default/runtime/src/main/resources/templates"));
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir))
                continue;
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path f : s.filter(Files::isRegularFile).toList()) {
                    String name = f.getFileName().toString();
                    if (!(name.endsWith(".html") || name.endsWith(".md") || name.endsWith(".adoc")))
                        continue;
                    String rel = root.relativize(f).toString();
                    if (rel.contains("marketplace/plugins/image.md"))
                        continue; // docs page showing escaped examples
                    String content = Files.readString(f);
                    String id = templateId(root, f);
                    String kind = kind(rel);
                    Matcher m = IMG_TAG.matcher(content);
                    while (m.find()) {
                        String src = null, preset = "default";
                        boolean literal = false;
                        Matcher p = PARAM.matcher(m.group(1));
                        while (p.find()) {
                            if (p.group(5) != null) { // positional literal
                                src = p.group(5).substring(1, p.group(5).length() - 1);
                                literal = true;
                            } else if (p.group(1).equals("src")) {
                                String v = p.group(2);
                                if (v.startsWith("'") || v.startsWith("\"")) {
                                    src = v.substring(1, v.length() - 1);
                                    literal = true;
                                } else {
                                    src = v;
                                }
                            } else if (p.group(1).equals("preset")) {
                                preset = p.group(3) != null ? p.group(3) : p.group(4) != null ? p.group(4) : p.group(2);
                            }
                        }
                        usages.add(new Usage(id, kind, src, literal, preset, m.group()));
                    }
                    m = IMG_NS.matcher(content);
                    while (m.find()) {
                        String src = m.group(1).trim();
                        boolean literal = src.startsWith("'") || src.startsWith("\"");
                        if (literal)
                            src = src.substring(1, src.length() - 1);
                        String preset = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : "default";
                        usages.add(new Usage(id, kind, src, literal, preset, m.group()));
                    }
                }
            }
        }
        return usages;
    }

    static String templateId(Path root, Path f) {
        String rel = root.relativize(f).toString().replace('\\', '/');
        int i = rel.indexOf("/templates/");
        if (i >= 0)
            rel = rel.substring(i + "/templates/".length());
        else if (rel.startsWith("blog/content/"))
            rel = "content/" + rel.substring("blog/content/".length());
        return rel.replaceAll("\\.(html|md|adoc)$", "");
    }

    static String kind(String rel) {
        if (rel.contains("/layouts/") || rel.contains("/theme-layouts/"))
            return "layout";
        if (rel.contains("/tags/") || rel.contains("/partials/"))
            return "tag";
        if (rel.startsWith("blog/content/"))
            return "page";
        return "other";
    }

    // ---------- light model ----------
    public static class Site {
        final Map<String, Object> data;
        final Path publicImages;

        Site(Map<String, Object> data, Path publicImages) {
            this.data = data;
            this.publicImages = publicImages;
        }

        public Map<String, Object> data() {
            return data;
        }

        public String url() {
            return "/";
        }

        public String image() {
            Object v = data.get("image");
            return v == null ? null : image(v);
        }

        public String image(Object name) {
            String n = String.valueOf(name);
            if (n.startsWith("http"))
                return n;
            return "/images/" + (n.startsWith("/") ? n.substring(1) : n);
        }

        Path imageFile(String src) {
            if (src == null || src.startsWith("http"))
                return null;
            if (src.startsWith("/images/"))
                return publicImages.resolve(src.substring("/images/".length()));
            return null;
        }
    }

    public static class Page {
        final String id;
        final String url;
        final String layout;
        final String collection;
        final Map<String, Object> data;
        final Path dir;
        final boolean dirPage;
        final Site site;
        final Path source;

        Page(String id, String url, String layout, String collection, Map<String, Object> data, Path dir, boolean dirPage,
                Site site, Path source) {
            this.id = id;
            this.url = url;
            this.layout = layout;
            this.collection = collection;
            this.data = data;
            this.dir = dir;
            this.dirPage = dirPage;
            this.site = site;
            this.source = source;
        }

        public Map<String, Object> data() {
            return data;
        }

        public String url() {
            return url;
        }

        public String title() {
            return String.valueOf(data.get("title"));
        }

        public Site site() {
            return site;
        }

        public String image() {
            Object v = data.get("image");
            if (v == null)
                v = data.get("img");
            if (v == null)
                v = data.get("picture");
            return v == null ? null : image(v);
        }

        /** Mimics Roq: full url as is, absolute → site images, attached file → page url, else site images */
        public String image(Object name) {
            String n = String.valueOf(name);
            if (n.startsWith("http"))
                return n;
            if (n.startsWith("/"))
                return site.image(n.substring(1));
            if (attachment(n) != null)
                return url + n;
            return site.image(n);
        }

        Path attachment(String n) {
            if (dirPage) {
                Path p = dir.resolve(n);
                if (Files.isRegularFile(p))
                    return p;
            } else {
                // file page: a `_`-prefixed sibling (as in blog/content/_image.jpg next to create.html)
                Path p = dir.resolve(n);
                if (n.startsWith("_") && Files.isRegularFile(p))
                    return p;
            }
            return null;
        }

        Path imageFile(String src) {
            if (src == null || src.startsWith("http"))
                return null;
            if (src.startsWith(url) && !url.equals("/")) {
                return attachment(src.substring(url.length()));
            }
            if (src.startsWith("/images/"))
                return site.imageFile(src);
            if (url.equals("/"))
                return attachment(src.substring(1));
            return null;
        }
    }

    static Map<String, Object> frontMatter(Path f) throws IOException {
        List<String> lines = Files.readAllLines(f);
        if (lines.isEmpty() || !lines.get(0).trim().equals("---"))
            return new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("---"))
                break;
            sb.append(lines.get(i)).append('\n');
        }
        try {
            Object o = new Yaml().load(sb.toString());
            return o instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
        } catch (Exception e) {
            System.err.println("  ! YAML error in " + f + ": " + e.getMessage().lines().findFirst().orElse(""));
            return new LinkedHashMap<>();
        }
    }

    static List<Page> scanPages(Path content, Site site) throws IOException {
        List<Page> pages = new ArrayList<>();
        try (Stream<Path> s = Files.walk(content)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                String name = f.getFileName().toString();
                if (!(name.endsWith(".html") || name.endsWith(".md") || name.endsWith(".adoc")))
                    continue;
                if (name.startsWith("_"))
                    continue;
                String rel = content.relativize(f).toString().replace('\\', '/');
                boolean dirPage = name.startsWith("index.");
                String collection = rel.startsWith("posts/") ? "posts" : null;
                String urlPath = dirPage ? rel.substring(0, rel.lastIndexOf('/') + 1) : rel.replaceAll("\\.\\w+$", "") + "/";
                if (collection != null)
                    urlPath = urlPath.replaceAll("^posts/\\d{4}-\\d{2}-\\d{2}-", "posts/");
                if (rel.startsWith("index."))
                    urlPath = "";
                Map<String, Object> fm = frontMatter(f);
                String layout = fm.containsKey("layout") ? String.valueOf(fm.get("layout"))
                        : collection != null ? "post" : null;
                pages.add(new Page("content/" + rel, "/" + urlPath, layout, collection, fm, f.getParent(), dirPage, site,
                        f));
            }
        }
        return pages;
    }

    // ---------- layout chain ----------
    static Map<String, String> layoutParents(Path root) throws IOException {
        // template id -> parent template id
        Map<String, String> parents = new LinkedHashMap<>();
        Path local = root.resolve("blog/templates/layouts");
        Path theme = root.resolve("roq-theme/default/runtime/src/main/resources/templates/theme-layouts/roq-default");
        for (Path dir : List.of(local, theme)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path f : s.filter(p -> p.toString().endsWith(".html")).toList()) {
                    Map<String, Object> fm = frontMatter(f);
                    String id = templateId(root, f);
                    String parent = null;
                    if (fm.containsKey("theme-layout"))
                        parent = "theme-layouts/roq-default/" + fm.get("theme-layout");
                    else if (fm.containsKey("layout"))
                        parent = resolveLayout(root, String.valueOf(fm.get("layout")));
                    parents.put(id, parent);
                }
            }
        }
        return parents;
    }

    static String resolveLayout(Path root, String name) {
        if (Files.exists(root.resolve("blog/templates/layouts/" + name + ".html")))
            return "layouts/" + name;
        if (Files.exists(root.resolve(
                "roq-theme/default/runtime/src/main/resources/templates/theme-layouts/roq-default/" + name + ".html")))
            return "theme-layouts/roq-default/" + name;
        return "?/" + name;
    }

    static List<String> chain(Path root, Map<String, String> parents, String layoutName) {
        List<String> chain = new ArrayList<>();
        String cur = layoutName == null ? null : resolveLayout(root, layoutName);
        while (cur != null && !chain.contains(cur)) {
            chain.add(cur);
            cur = parents.get(cur);
        }
        return chain;
    }

    // ---------- main ----------
    public static void main(String... args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path content = root.resolve("blog/content");

        List<Usage> usages = new ArrayList<>(extract(root));
        if (args.length > 1 && args[1].equals("--synthetic")) {
            // Harder expression shapes to validate the Qute-based evaluation
            usages.add(new Usage("theme-layouts/roq-default/post", "layout", "page.image('_image.jpg')", false, "roq-content", "{#img src=page.image('_image.jpg') ...}"));
            usages.add(new Usage("theme-layouts/roq-default/post", "layout", "page.data.cover ?: page.image", false, "roq-hero", "{#img src=page.data.cover ?: page.image ...}"));
            usages.add(new Usage("theme-layouts/roq-default/main", "layout", "page.image ?: site.image", false, "roq-hero", "{#img src=page.image ?: site.image ...}"));
            usages.add(new Usage("theme-layouts/roq-default/main", "layout", "site.image", false, "roq-hero", "{#img src=site.image ...}"));
            usages.add(new Usage("theme-layouts/roq-default/main", "layout", "page.data['image']", false, "roq-avatar", "{#img src=page.data['image'] ...}"));
            usages.add(new Usage("theme-layouts/roq-default/main", "layout", "inject:someBean.image", false, "roq-hero", "{#img src=inject:someBean.image ...}"));
            usages.add(new Usage("theme-layouts/roq-default/main", "layout", "page.author.avatar", false, "roq-avatar", "{#img src=page.author.avatar ...}"));
        }
        System.out.println("== Image usages found: " + usages.size());
        for (Usage u : usages) {
            System.out.printf("  [%s] %s : src=%s%s preset=%s%n", u.kind, u.templateId, u.srcExpr,
                    u.literal ? " (literal)" : " (expression)", u.preset);
        }

        Site site = new Site(frontMatter(content.resolve("index.html")), root.resolve("blog/public/images"));
        List<Page> pages = scanPages(content, site);
        Map<String, String> parents = layoutParents(root);
        System.out.println("\n== Pages: " + pages.size() + " (documents: "
                + pages.stream().filter(p -> p.collection != null).count() + ")");
        Map<String, List<Page>> byLayout = new LinkedHashMap<>();
        for (Page p : pages) {
            for (String l : chain(root, parents, p.layout)) {
                byLayout.computeIfAbsent(l, k -> new ArrayList<>()).add(p);
            }
        }
        byLayout.forEach((l, ps) -> System.out.printf("  layout %-40s used by %d pages%n", l, ps.size()));

        Engine engine = Engine.builder().addDefaults().addValueResolver(new ReflectionValueResolver())
                .strictRendering(true).timeout(2000).build();

        System.out.println("\n== Evaluation");
        Set<String> toGenerate = new TreeSet<>();
        Map<String, Path> files = new TreeMap<>();
        int dynamic = 0, missing = 0;
        for (Usage u : usages) {
            System.out.printf("%n-- %s in %s (preset=%s)%n", u.raw.length() > 70 ? u.raw.substring(0, 70) + "..." : u.raw,
                    u.templateId, u.preset);
            if (u.literal) {
                System.out.println("   literal src: already handled by the existing scanner");
                continue;
            }
            String rootVar = u.srcExpr.split("[.(\\[ ]")[0];
            List<Page> contexts;
            String reason;
            switch (u.kind) {
                case "page" -> {
                    contexts = pages.stream().filter(p -> p.id.equals(u.templateId)).toList();
                    reason = "page template: bind to itself";
                }
                case "layout" -> {
                    contexts = byLayout.getOrDefault(u.templateId, List.of());
                    reason = "layout: bind to every page whose layout chain includes it";
                }
                default -> {
                    if (rootVar.equals("page") || rootVar.equals("site")) {
                        contexts = pages;
                        reason = "tag with page/site: bind to all pages";
                    } else {
                        contexts = pages.stream().filter(p -> p.collection != null).toList();
                        reason = "tag with unknown var '" + rootVar + "': bind to all collection documents";
                    }
                }
            }
            System.out.println("   " + reason + " -> " + contexts.size() + " contexts");
            int ok = 0, none = 0, failed = 0;
            for (Page p : contexts) {
                String src;
                try {
                    src = engine.parse("{" + u.srcExpr + "}")
                            .data(rootVar, p)
                            .data("page", p)
                            .data("site", site)
                            .render();
                } catch (TemplateException e) {
                    failed++;
                    System.out.println("   ✗ " + p.id + " : " + e.getMessage().lines().findFirst().orElse(""));
                    continue;
                }
                if (src.isEmpty()) {
                    none++;
                    continue;
                }
                Path file = p.imageFile(src);
                boolean exists = file != null && Files.isRegularFile(file);
                String key = src + " | " + u.preset;
                if (exists) {
                    ok++;
                    toGenerate.add(key);
                    files.put(key, file);
                } else if (src.startsWith("http")) {
                    ok++;
                    toGenerate.add(key);
                } else {
                    missing++;
                    System.out.println("   ? " + p.id + " -> " + src + " (file not found)");
                }
            }
            dynamic += failed;
            System.out.printf("   resolved=%d  no-image=%d  unresolvable=%d%n", ok, none, failed);
        }

        System.out.println("\n== Build-time pre-generation plan (src | preset) : " + toGenerate.size());
        for (String k : toGenerate) {
            Path f = files.get(k);
            System.out.printf("   %-80s <- %s%n", k, f == null ? "(remote url)" : root.relativize(f));
        }
        System.out.println("\n== Summary");
        System.out.println("   usages with expression src : "
                + usages.stream().filter(u -> !u.literal).count());
        System.out.println("   (src,preset) pairs pre-resolvable at build time : " + toGenerate.size());
        System.out.println("   contexts left dynamic (evaluation failed) : " + dynamic);
        System.out.println("   contexts pointing to a missing file : " + missing);
    }
}
