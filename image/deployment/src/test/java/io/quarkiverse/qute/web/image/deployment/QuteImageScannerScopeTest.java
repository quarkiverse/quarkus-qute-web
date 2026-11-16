package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;

/** The scanner scope of an images dir: a classpath prefix as is, a local dir relative to the project root. */
public class QuteImageScannerScopeTest {

    static final Path ROOT = Path.of("/project");

    @Test
    public void testClasspathDirIsItsPrefix() {
        assertEquals("web/static", QuteImageProcessor.scannerScope(ImagesDirBuildItem.of("web/static"),
                new ProjectRootBuildItem(ROOT)));
    }

    @Test
    public void testLocalDirIsRelativeToTheProjectRoot() {
        ProjectRootBuildItem root = new ProjectRootBuildItem(ROOT);
        assertEquals("site/public", QuteImageProcessor.scannerScope(
                ImagesDirBuildItem.of("", ROOT.resolve("site/public")), root));
        assertEquals("site/public", QuteImageProcessor.scannerScope(
                ImagesDirBuildItem.of("", Path.of("site/public")), root));
    }

    /** No project root (nothing above the output dir looks like a project): a local dir cannot be scanned. */
    @Test
    public void testLocalDirWithoutProjectRootIsAnError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> QuteImageProcessor.scannerScope(
                ImagesDirBuildItem.of("", ROOT.resolve("site/public")), new ProjectRootBuildItem(null)));
        assertEquals("Image directory /project/site/public needs a project root, none was found", e.getMessage());
    }
}
