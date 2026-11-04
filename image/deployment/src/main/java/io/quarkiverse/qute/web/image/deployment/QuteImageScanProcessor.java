package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionBlock;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.deployment.EffectiveTemplatePathsBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem;

public class QuteImageScanProcessor {

    public static final String TARGET_DIR_NAME = "qute-image/";

    @BuildStep
    QuteImageTargetDirBuildItem initTargetDir(OutputTargetBuildItem outputTarget, LaunchModeBuildItem launchMode) {
        final String targetDirName = TARGET_DIR_NAME + launchMode.getLaunchMode().getDefaultProfile();
        final Path targetDir = outputTarget.getOutputDirectory().resolve(targetDirName);
        try {
            FileUtil.deleteDirectory(targetDir);
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
                    .map(imageTagSectionMapper(config)).filter(Objects::nonNull).forEach(refs::add);
            // Scan {img:url(...)} and {img:tag(...)} namespace expressions
            analysis.findNodes(QuteImageScanProcessor::isImageNamespaceExpression).stream()
                    .map(imageNamespaceExprMapper(config)).filter(Objects::nonNull).forEach(refs::add);
            quteRuntimeTemplateBuildItemBuildProducer
                    .produce(new QuteImageTemplateToScanBuildItem(location, refs, analysis.path));
        }
    }

    private static Function<TemplateNode, ImageRef> imageTagSectionMapper(ImageConfig config) {
        return sectionNode -> {
            final List<SectionBlock> blocks = sectionNode.asSection().getBlocks();
            if (blocks.size() != 1) {
                throw new IllegalStateException("Expected exactly one section block for image but got " + blocks.size());
            }
            final Map<String, Expression> parameters = blocks.iterator().next().expressions;
            if (parameters.containsKey("src")) {
                Expression expr = parameters.get("src");
                Expression presetExpr = parameters.get("preset");
                if (!expr.isLiteral()) {
                    return null;
                }
                Object literal = expr.getLiteral();
                if (!(literal instanceof String file)) {
                    throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
                }
                String templateId = sectionNode.getOrigin().getTemplateId();
                return resolveImageRef(config, templateId, file, presetExpr, "{#img}");
            } else {
                throw new RuntimeException(
                        "Invalid image parameter list: " + parameters + " ('src' is required)");
            }
        };
    }

    private static Function<TemplateNode, ImageRef> imageNamespaceExprMapper(ImageConfig config) {
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
            if (!srcExpr.isLiteral()) {
                return null;
            }
            Object literal = srcExpr.getLiteral();
            if (!(literal instanceof String file)) {
                throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
            }
            Expression presetExpr = params.size() > 1 ? params.get(1) : null;
            String templateId = exprNode.getOrigin().getTemplateId();
            return resolveImageRef(config, templateId, file, presetExpr,
                    "{img:" + method.getName() + "(...)}");
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
