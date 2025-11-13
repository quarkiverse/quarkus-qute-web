package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageTargetDirBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.spi.items.ImageSourceDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.WhitelistDirBuildItem;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionBlock;
import io.quarkus.qute.SectionNode;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.deployment.EffectiveTemplatePathsBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem;

public class QuteImageScanProcessor {

    public static final String TARGET_DIR_NAME = "qute-image/";

    @BuildStep
    void initBuildDirs(CurateOutcomeBuildItem curateOutcome, List<ImageSourceDirBuildItem> imageSourceDirs,
            BuildProducer<WhitelistDirBuildItem> whitelistDirs) {

        Set<Path> outputPaths = new HashSet<>();
        outputPaths
                .addAll(curateOutcome.getApplicationModel().getApplicationModule().getMainSources().getOutputTree().getRoots());
        outputPaths
                .addAll(curateOutcome.getApplicationModel().getApplicationModule().getTestSources().getOutputTree().getRoots());
        for (WorkspaceModule workspaceModule : curateOutcome.getApplicationModel().getWorkspaceModules()) {
            outputPaths.addAll(workspaceModule.getMainSources().getOutputTree().getRoots());
        }
        outputPaths.forEach(path -> addBuildDirIfExists(whitelistDirs, path));

        for (ImageSourceDirBuildItem dir : imageSourceDirs) {
            if (!dir.isResource()) {
                addBuildDirIfExists(whitelistDirs, dir.basePath());
            }
        }
    }

    private static void addBuildDirIfExists(BuildProducer<WhitelistDirBuildItem> whitelistDirs, Path buildDir) {
        if (buildDir != null && Files.isDirectory(buildDir)) {
            whitelistDirs.produce(new WhitelistDirBuildItem(buildDir));
        }
    }

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
            if (templatePath.getSource() == null && templatePath.getFullPath() != null) {
                location = templatePath.getFullPath().toUri();
            } else {
                location = templatePath.getSource();
            }
            quteRuntimeTemplateBuildItemBuildProducer
                    .produce(new QuteImageTemplateToScanBuildItem(location,
                            analysis.findNodes(QuteImageScanProcessor::isImageSection).stream()
                                    .map(QuteImageScanProcessor::toImageTagSection).toList(),
                            analysis.path));
        }
    }

    private static ImageTagSection toImageTagSection(TemplateNode sectionNode) {

        final List<SectionBlock> blocks = sectionNode.asSection().getBlocks();
        if (blocks.size() != 1) {
            throw new IllegalStateException("Expected exactly one section block for image but got " + blocks.size());
        }
        final Map<String, Expression> parameters = blocks.iterator().next().expressions;
        if (parameters.containsKey("src")) {
            Expression expr = parameters.get("src");
            if (expr.isLiteral()) {
                Object literal = expr.getLiteral();
                if (literal instanceof String file) {
                    return new ImageTagSection((SectionNode) sectionNode, file);
                } else {
                    throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
                }
            } else {
                throw new RuntimeException("Invalid image parameter 'src': " + expr + " (must be a string literal)");
            }
        } else {
            throw new RuntimeException(
                    "Invalid image parameter list: " + parameters + " ('src' is required)");
        }

    }

    private static boolean isImageSection(TemplateNode templateNode) {
        return templateNode.isSection() && "image".equals(templateNode.asSection().getName());
    }

    // Kept in case we need it later
    //    private void findTemplatePath(String path, List<TemplatePathBuildItem> tp) {
    //        System.err.println("Looking for template " + path);
    //        for (TemplatePathBuildItem templatePathBuildItem : tp) {
    //            //            System.err.println(" Looking at " + templatePathBuildItem.getPath());
    //            if (path.equals(templatePathBuildItem.getPath())) {
    //                System.err.println("  Full path: " + templatePathBuildItem.getFullPath());
    //                return;
    //            }
    //        }
    //        System.err.println(" Not Found");
    //    }

}
