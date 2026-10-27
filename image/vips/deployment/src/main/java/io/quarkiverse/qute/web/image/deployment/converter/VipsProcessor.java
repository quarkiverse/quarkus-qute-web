package io.quarkiverse.qute.web.image.deployment.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.ImageConverterBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;

public class VipsProcessor {

    private static final Logger LOGGER = Logger.getLogger(VipsProcessor.class);

    private static final List<LibOverride> LIB_OVERRIDES = List.of(
            new LibOverride("vips", "vipsffm.libpath.vips.override"),
            new LibOverride("glib-2.0", "vipsffm.libpath.glib.override"),
            new LibOverride("gobject-2.0", "vipsffm.libpath.gobject.override"));

    @BuildStep
    ImageConverterBuildItem initImageConverter(List<QuteImageTemplateToScanBuildItem> templateToScan) {
        if (templateToScan.isEmpty()) {
            return null;
        }
        autoDetectLibVips();
        return new ImageConverterBuildItem(new io.quarkiverse.qute.web.image.converter.vips.VipsConverter());
    }

    static void autoDetectLibVips() {
        for (var lib : LIB_OVERRIDES) {
            if (System.getProperty(lib.systemProperty()) != null) {
                continue;
            }
            resolveWithPkgConfig(lib.pkgName())
                    .ifPresent(path -> {
                        LOGGER.debugf("Auto-detected %s at %s", lib.pkgName(), path);
                        System.setProperty(lib.systemProperty(), path.toString());
                    });
        }
    }

    private static Optional<Path> resolveWithPkgConfig(String pkgName) {
        try {
            Process process = new ProcessBuilder("pkg-config", "--variable=libdir", pkgName)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (process.waitFor() != 0 || output.isBlank()) {
                return Optional.empty();
            }
            Path libFile = Path.of(output, System.mapLibraryName(pkgName));
            if (Files.exists(libFile)) {
                return Optional.of(libFile);
            }
        } catch (Exception e) {
            LOGGER.debugf("pkg-config lookup failed for %s: %s", pkgName, e.getMessage());
        }
        return Optional.empty();
    }

    record LibOverride(String pkgName, String systemProperty) {
    }

}
