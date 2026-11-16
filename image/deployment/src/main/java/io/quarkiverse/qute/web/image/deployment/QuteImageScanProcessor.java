package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageExpressionBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.spi.items.QuteImageTemplateLocationBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionBlock;
import io.quarkus.qute.SectionNode;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.UserTagSectionHelper;
import io.quarkus.qute.deployment.EffectiveTemplatePathsBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem;
import io.quarkus.qute.runtime.QuteConfig;

public class QuteImageScanProcessor {

    @BuildStep
    QuteImageTargetDirBuildItem initTargetDir(ImageConfig config, OutputTargetBuildItem outputTarget) {
        Path configured = Path.of(config.buildDir());
        final Path targetDir = outputTarget.getOutputDirectory().resolve(configured).normalize();
        try {
            // persistent across builds on purpose: existing variants are reused (see QuteImageProcessor)
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new QuteImageTargetDirBuildItem(targetDir);
    }

    @BuildStep
    @SuppressWarnings("deprecation")
    void scanRuntimeQuteTemplates(
            ImageConfig config,
            QuteConfig quteConfig,
            EffectiveTemplatePathsBuildItem effectiveTemplatePaths,
            BuildProducer<QuteImageTemplateToScanBuildItem> templatesToScan,
            BuildProducer<QuteImageExpressionBuildItem> expressionProducer,
            TemplatesAnalysisBuildItem templatesAnalysisBuildItem,
            List<QuteImageTemplateLocationBuildItem> declaredLocations) {
        final Map<String, TemplatePathBuildItem> byId = effectiveTemplatePaths.getTemplatePaths().stream()
                .collect(Collectors.toMap(TemplatePathBuildItem::getPath, Function.identity()));
        final Map<String, URI> locationsById = declaredLocations.stream()
                .collect(Collectors.toMap(QuteImageTemplateLocationBuildItem::templateId,
                        QuteImageTemplateLocationBuildItem::source, (a, b) -> a));
        final TemplateGraph graph = new TemplateGraph(quteConfig, effectiveTemplatePaths, templatesAnalysisBuildItem);
        final Map<String, List<ImageUsage>> usagesByPath = new HashMap<>();
        // Collect the templates Qute validated at build time, and pass them on to the processor
        for (TemplatesAnalysisBuildItem.TemplateAnalysis analysis : templatesAnalysisBuildItem.getAnalysis()) {
            final TemplatePathBuildItem templatePath = byId.get(analysis.path);
            if (templatePath == null) {
                continue;
            }
            final URI location = resolveTemplateLocation(templatePath, locationsById.get(analysis.path));
            // Literals resolve in the template where they are written
            List<ImageRef> refs = new ArrayList<>();
            for (ImageUsage usage : usagesByPath.computeIfAbsent(analysis.path, p -> findImageUsages(analysis))) {
                if (usage.isLiteral()) {
                    refs.add(new ImageRef(usage.literalPath(), presetName(usage, analysis.path), usage.context()));
                }
            }
            templatesToScan.produce(new QuteImageTemplateToScanBuildItem(location, refs, analysis.path));
            // Expressions are reported for every template that renders them: the template itself and the
            // ones that reach it through {#include} and user tags, so a host can bind the data of the
            // top-level template without resolving layouts on its own
            for (TemplatesAnalysisBuildItem.TemplateAnalysis reached : graph.reachableFrom(analysis)) {
                for (ImageUsage usage : usagesByPath.computeIfAbsent(reached.path, p -> findImageUsages(reached))) {
                    if (!usage.isLiteral()) {
                        expressionProducer.produce(toExpressionItem(analysis.path, reached.path, usage));
                    }
                }
            }
        }
    }

    /**
     * A {@code {#img src=...}} section or a {@code {img:xxx(...)}} namespace expression.
     */
    record ImageUsage(Expression src, Expression preset, String context) {

        boolean isLiteral() {
            return src.isLiteral();
        }

        String literalPath() {
            Object literal = src.getLiteral();
            if (!(literal instanceof String file)) {
                throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
            }
            return file;
        }
    }

    private static List<ImageUsage> findImageUsages(TemplatesAnalysisBuildItem.TemplateAnalysis analysis) {
        List<ImageUsage> usages = new ArrayList<>();
        for (TemplateNode node : analysis.findNodes(QuteImageScanProcessor::isImageSection)) {
            final List<SectionBlock> blocks = node.asSection().getBlocks();
            if (blocks.size() != 1) {
                throw new IllegalStateException("Expected exactly one section block for image but got " + blocks.size());
            }
            final Map<String, Expression> parameters = blocks.iterator().next().expressions;
            Expression src = parameters.get("src");
            if (src == null) {
                throw new RuntimeException("{#img} without a source in template " + analysis.path
                        + ": use {#img 'photo.jpg' /} or {#img src=... /}");
            }
            usages.add(new ImageUsage(src, parameters.get("preset"), "{#img}"));
        }
        for (TemplateNode node : analysis.findNodes(QuteImageScanProcessor::isImageNamespaceExpression)) {
            Expression expr = node.asExpression().getExpressions().get(0);
            List<Expression.Part> parts = expr.getParts();
            if (parts.isEmpty() || !parts.get(0).isVirtualMethod()) {
                continue;
            }
            Expression.VirtualMethodPart method = parts.get(0).asVirtualMethod();
            List<Expression> params = method.getParameters();
            if (params.isEmpty() || "lookup".equals(method.getName())) {
                // lookup is the {#img} user tag's own call, the tag usage is what gets reported
                continue;
            }
            usages.add(new ImageUsage(params.get(0), params.size() > 1 ? params.get(1) : null,
                    "{img:" + method.getName() + "(...)}"));
        }
        return usages;
    }

    /**
     * Which templates a template renders, through {@code {#include}} sections with a fixed id and user tag calls.
     * Dynamic includes ({@code {#include _id=...}}) cannot be followed and are ignored.
     */
    static final class TemplateGraph {

        private final QuteConfig quteConfig;
        private final Map<String, TemplatesAnalysisBuildItem.TemplateAnalysis> byPath = new HashMap<>();
        private final Map<String, TemplatesAnalysisBuildItem.TemplateAnalysis> byPathWithoutSuffix = new HashMap<>();
        private final Map<String, String> tagPathsByName = new HashMap<>();

        TemplateGraph(QuteConfig quteConfig, EffectiveTemplatePathsBuildItem effectiveTemplatePaths,
                TemplatesAnalysisBuildItem templatesAnalysis) {
            this.quteConfig = quteConfig;
            for (TemplatesAnalysisBuildItem.TemplateAnalysis analysis : templatesAnalysis.getAnalysis()) {
                byPath.put(analysis.path, analysis);
                byPathWithoutSuffix.putIfAbsent(withoutSuffix(analysis.path), analysis);
            }
            for (TemplatePathBuildItem path : effectiveTemplatePaths.getTemplatePaths()) {
                if (path.isTag()) {
                    tagPathsByName.put(withoutSuffix(path.getPath().substring(TAGS_PREFIX.length())), path.getPath());
                }
            }
        }

        /**
         * @return the template itself followed by every template it reaches, each once
         */
        List<TemplatesAnalysisBuildItem.TemplateAnalysis> reachableFrom(TemplatesAnalysisBuildItem.TemplateAnalysis root) {
            Map<String, TemplatesAnalysisBuildItem.TemplateAnalysis> visited = new LinkedHashMap<>();
            collect(root, visited);
            return new ArrayList<>(visited.values());
        }

        private void collect(TemplatesAnalysisBuildItem.TemplateAnalysis analysis,
                Map<String, TemplatesAnalysisBuildItem.TemplateAnalysis> visited) {
            if (visited.putIfAbsent(analysis.path, analysis) != null) {
                return;
            }
            for (TemplateNode node : analysis.findNodes(TemplateNode::isSection)) {
                TemplatesAnalysisBuildItem.TemplateAnalysis target = target(node.asSection());
                if (target != null) {
                    collect(target, visited);
                }
            }
        }

        private TemplatesAnalysisBuildItem.TemplateAnalysis target(SectionNode section) {
            if (section.getHelper() instanceof UserTagSectionHelper) {
                String tagPath = tagPathsByName.get(section.getName());
                return tagPath == null ? null : byPath.get(tagPath);
            }
            if (!"include".equals(section.getName()) || section.getBlocks().isEmpty()) {
                return null;
            }
            Map<String, String> params = section.getBlocks().get(0).parameters;
            String id = params.get("template");
            if (id == null || params.containsKey("_id")) {
                return null;
            }
            if (id.length() > 1 && (id.charAt(0) == '\'' || id.charAt(0) == '"')) {
                id = id.substring(1, id.length() - 1);
            }
            int fragment = id.lastIndexOf('$');
            if (fragment >= 0) {
                id = id.substring(0, fragment);
            }
            if (id.isEmpty()) {
                return null;
            }
            TemplatesAnalysisBuildItem.TemplateAnalysis exact = byPath.get(id);
            return exact != null ? exact : byPathWithoutSuffix.get(id);
        }

        private String withoutSuffix(String path) {
            for (String suffix : quteConfig.suffixes()) {
                if (path.endsWith("." + suffix)) {
                    return path.substring(0, path.length() - suffix.length() - 1);
                }
            }
            return path;
        }
    }

    private static final String TAGS_PREFIX = "tags/";

    private static QuteImageExpressionBuildItem toExpressionItem(String templateId, String originTemplateId,
            ImageUsage usage) {
        Expression srcExpr = usage.src();
        String origin = usage.context() + " at line " + srcExpr.getOrigin().getLine()
                + (originTemplateId.equals(templateId) ? "" : " in " + originTemplateId);
        return new QuteImageExpressionBuildItem(templateId, toParsableString(srcExpr),
                presetName(usage, originTemplateId), origin);
    }

    /**
     * {@link Expression#toOriginalString()} renders infix operators with their symbolic virtual method names
     * ({@code a.?:(b)} for {@code a ?: b}), which the parser does not accept back. This renders the same
     * expression with the word aliases Qute's resolvers also answer to, so the result can be parsed again.
     */
    static String toParsableString(Expression expr) {
        StringBuilder builder = new StringBuilder();
        if (expr.getNamespace() != null) {
            builder.append(expr.getNamespace()).append(':');
        }
        for (Iterator<Expression.Part> iterator = expr.getParts().iterator(); iterator.hasNext();) {
            Expression.Part part = iterator.next();
            if (part.isVirtualMethod()) {
                Expression.VirtualMethodPart method = part.asVirtualMethod();
                builder.append(parsableMethodName(method.getName())).append('(');
                for (Iterator<Expression> params = method.getParameters().iterator(); params.hasNext();) {
                    builder.append(toParsableString(params.next()));
                    if (params.hasNext()) {
                        builder.append(", ");
                    }
                }
                builder.append(')');
            } else {
                builder.append(part.getName());
            }
            if (iterator.hasNext()) {
                builder.append('.');
            }
        }
        return builder.toString();
    }

    private static String parsableMethodName(String name) {
        return switch (name) {
            case "?:", ":" -> "or";
            case "?" -> "ifTruthy";
            default -> name;
        };
    }

    /** The preset name: the literal written in the template, or {@code default} when absent or not a literal. */
    /** The preset must be known at build time: a literal, or none for the default preset. */
    private static String presetName(ImageUsage usage, String templateId) {
        Expression preset = usage.preset();
        if (preset == null) {
            return "default";
        }
        if (preset.isLiteral() && preset.getLiteral() instanceof String name) {
            return name;
        }
        throw new RuntimeException("The preset of %s in template '%s' must be a string literal, got '%s'"
                .formatted(usage.context(), templateId, preset.toOriginalString()));
    }

    /**
     * Where relative image paths of a template resolve from: the source declared by the host (see
     * {@link QuteImageTemplateLocationBuildItem}), else the template source, else the template file itself. A
     * {@code file:} location is a local directory; a {@code jar:} location is read from the classpath (see
     * {@link QuteImageProcessor}).
     */
    private static URI resolveTemplateLocation(TemplatePathBuildItem templatePath, URI declaredSource) {
        if (declaredSource != null) {
            return resolvable(declaredSource) ? declaredSource : null;
        }
        if (templatePath.getSource() != null) {
            return resolvable(templatePath.getSource()) ? templatePath.getSource() : null;
        }
        if (templatePath.getFullPath() != null && isLocalPath(templatePath.getFullPath())) {
            return templatePath.getFullPath().toUri();
        }
        return null;
    }

    /** A location relative sources can be read from: a local file, or a classpath resource. */
    private static boolean resolvable(URI uri) {
        return isLocalFile(uri) || resourcePath(uri) != null;
    }

    static boolean isLocalFile(URI uri) {
        return toLocalPath(uri) != null;
    }

    /**
     * The classpath resource path of a non local template source: the entry of a {@code jar:} URI, or the path of
     * a {@code classpath:} URI; null for anything else.
     */
    static String resourcePath(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return null;
        }
        String s = uri.toString();
        String path = switch (uri.getScheme()) {
            case "jar" -> s.indexOf("!/") > 0 ? s.substring(s.indexOf("!/") + 2) : null;
            case "classpath" -> uri.getSchemeSpecificPart();
            default -> null;
        };
        if (path == null) {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    static Path toLocalPath(URI uri) {
        if (uri == null) {
            return null;
        }
        Path path;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            path = Paths.get(uri);
        } else if (uri.getScheme() == null) {
            path = Path.of(uri.getPath());
        } else {
            return null;
        }
        return Files.exists(path) ? path : null;
    }

    private static boolean isLocalPath(Path path) {
        return path.getFileSystem() == FileSystems.getDefault() && Files.exists(path);
    }

    private static boolean isImageSection(TemplateNode templateNode) {
        return templateNode.isSection() && "img".equals(templateNode.asSection().getName());
    }

    private static boolean isImageNamespaceExpression(TemplateNode templateNode) {
        if (!templateNode.isExpression()) {
            return false;
        }
        Expression expr = templateNode.asExpression().getExpressions().get(0);
        return "img".equals(expr.getNamespace());
    }

}
