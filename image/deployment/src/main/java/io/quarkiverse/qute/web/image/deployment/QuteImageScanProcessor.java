package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageExpressionBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionBlock;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.deployment.EffectiveTemplatePathsBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem;

public class QuteImageScanProcessor {

    @BuildStep
    QuteImageTargetDirBuildItem initTargetDir(ImageConfig config, OutputTargetBuildItem outputTarget) {
        Path configured = Path.of(config.buildDir());
        final Path targetDir = configured.isAbsolute() ? configured
                : outputTarget.getOutputDirectory().resolve(configured);
        try {
            // persistent across builds on purpose: existing variants are reused (see QuteImageProcessor)
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new QuteImageTargetDirBuildItem(targetDir);
    }

    @BuildStep
    void scanRuntimeQuteTemplates(
            ImageConfig config,
            EffectiveTemplatePathsBuildItem effectiveTemplatePaths,
            BuildProducer<QuteImageTemplateToScanBuildItem> quteRuntimeTemplateBuildItemBuildProducer,
            BuildProducer<QuteImageExpressionBuildItem> expressionProducer,
            TemplatesAnalysisBuildItem templatesAnalysisBuildItem) {
        final Map<String, TemplatePathBuildItem> byId = effectiveTemplatePaths.getTemplatePaths().stream()
                .collect(Collectors.toMap(TemplatePathBuildItem::getPath, Function.identity()));
        // Collect runtime templates that are build-time validated, and pass them on to the normal deployment
        // processor (which doesn't depend on quarkus-qute unlike this module)
        for (TemplatesAnalysisBuildItem.TemplateAnalysis analysis : templatesAnalysisBuildItem.getAnalysis()) {
            final URI location;
            final TemplatePathBuildItem templatePath = byId.get(analysis.path);
            if (templatePath == null) {
                continue;
            }
            location = resolveTemplateLocation(templatePath);
            List<ImageRef> refs = new ArrayList<>();
            // Scan {#img} section tags
            analysis.findNodes(QuteImageScanProcessor::isImageSection).stream()
                    .map(imageTagSectionMapper(config, expressionProducer)).filter(Objects::nonNull).forEach(refs::add);
            // Scan {img:path(...)} and {img:tag(...)} namespace expressions
            analysis.findNodes(QuteImageScanProcessor::isImageNamespaceExpression).stream()
                    .map(imageNamespaceExprMapper(config, expressionProducer)).filter(Objects::nonNull)
                    .forEach(refs::add);
            quteRuntimeTemplateBuildItemBuildProducer
                    .produce(new QuteImageTemplateToScanBuildItem(location, refs, analysis.path));
        }
    }

    private static Function<TemplateNode, ImageRef> imageTagSectionMapper(ImageConfig config,
            BuildProducer<QuteImageExpressionBuildItem> expressionProducer) {
        return sectionNode -> {
            final List<SectionBlock> blocks = sectionNode.asSection().getBlocks();
            if (blocks.size() != 1) {
                throw new IllegalStateException("Expected exactly one section block for image but got " + blocks.size());
            }
            final Map<String, Expression> parameters = blocks.iterator().next().expressions;
            if (parameters.containsKey("src")) {
                Expression expr = parameters.get("src");
                Expression presetExpr = parameters.get("preset");
                String templateId = sectionNode.getOrigin().getTemplateId();
                if (!expr.isLiteral()) {
                    expressionProducer.produce(toExpressionItem(templateId, expr, presetExpr, "{#img}"));
                    return null;
                }
                Object literal = expr.getLiteral();
                if (!(literal instanceof String file)) {
                    throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
                }
                if (ImageUtils.isRemote(file)) {
                    return null;
                }
                return resolveImageRef(config, templateId, file, presetExpr, "{#img}");
            } else {
                throw new RuntimeException(
                        "Invalid image parameter list: " + parameters + " ('src' is required)");
            }
        };
    }

    private static Function<TemplateNode, ImageRef> imageNamespaceExprMapper(ImageConfig config,
            BuildProducer<QuteImageExpressionBuildItem> expressionProducer) {
        return exprNode -> {
            Expression expr = exprNode.asExpression().getExpressions().get(0);
            List<Expression.Part> parts = expr.getParts();
            if (parts.isEmpty() || !parts.get(0).isVirtualMethod()) {
                return null;
            }
            Expression.VirtualMethodPart method = parts.get(0).asVirtualMethod();
            List<Expression> params = method.getParameters();
            if (params.isEmpty()) {
                return null;
            }
            Expression srcExpr = params.get(0);
            Expression presetExpr = params.size() > 1 ? params.get(1) : null;
            String templateId = exprNode.getOrigin().getTemplateId();
            String context = "{img:" + method.getName() + "(...)}";
            if (!srcExpr.isLiteral()) {
                expressionProducer.produce(toExpressionItem(templateId, srcExpr, presetExpr, context));
                return null;
            }
            Object literal = srcExpr.getLiteral();
            if (!(literal instanceof String file)) {
                throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
            }
            if (ImageUtils.isRemote(file)) {
                return null;
            }
            return resolveImageRef(config, templateId, file, presetExpr, context);
        };
    }

    private static QuteImageExpressionBuildItem toExpressionItem(String templateId, Expression srcExpr,
            Expression presetExpr, String context) {
        String presetExpression = presetExpr == null ? "'default'" : toParsableString(presetExpr);
        List<Expression.Part> parts = srcExpr.getParts();
        String rootTypeInfo = parts.isEmpty() ? null : parts.get(0).getTypeInfo();
        String origin = context + " at line " + srcExpr.getOrigin().getLine();
        return new QuteImageExpressionBuildItem(templateId, toParsableString(srcExpr), presetExpression,
                rootTypeInfo, origin);
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

    private static ImageRef resolveImageRef(ImageConfig config, String templateId,
            String file, Expression presetExpr, String context) {
        String presetName = "default";
        PresetConfig presetConfig = PresetConfig.DEFAULT;
        if (presetExpr != null && presetExpr.isLiteral()
                && presetExpr.getLiteral() instanceof String pName) {
            presetName = pName;
            if ("default".equals(pName)) {
                presetConfig = config.presets().getOrDefault(pName, PresetConfig.DEFAULT);
            } else {
                PresetConfig resolved = config.presets().get(pName);
                if (resolved == null) {
                    throw new RuntimeException(
                            "Unknown preset '%s' in %s. Available presets: %s"
                                    .formatted(pName, context, config.presets().keySet()));
                }
                presetConfig = resolved;
            }
        }
        return new ImageRef(templateId, file, presetName, presetConfig);
    }

    private static URI resolveTemplateLocation(TemplatePathBuildItem templatePath) {
        if (templatePath.getSource() != null) {
            return isLocalFile(templatePath.getSource()) ? templatePath.getSource() : null;
        }
        if (templatePath.getFullPath() != null && isLocalPath(templatePath.getFullPath())) {
            return templatePath.getFullPath().toUri();
        }
        return null;
    }

    static boolean isLocalFile(URI uri) {
        return toLocalPath(uri) != null;
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
