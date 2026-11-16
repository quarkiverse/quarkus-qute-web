package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The two ways a host registers an images dir, both served under a url prefix: a classpath dir, and a dir given by
 * its absolute filesystem path (the scanner indexes it relative to the project root). A glob match in either is
 * recorded under its url, the key a template lookup uses.
 */
public class QuteImageDirsTest {

    static final Path LOCAL = Path.of("target/test-classes/web/static").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> {
                        context.produce(ImagesDirBuildItem.of("web/static", "assets"));
                        context.produce(ImagesDirBuildItem.of("local", LOCAL));
                    })
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new File("src/test/resources/web/static/images"), "web/static/images"));

    @Inject
    Images images;

    @Test
    public void testClasspathDirGlobMatchIsKeyedByItsUrl() {
        ImageTag tag = images.get(null, "/assets/images/white_1920_1080.png", "small");
        assertNotNull(tag, "glob match not found under its url");
        assertEquals("/foo/bar/assets/images/white_1920_1080.png", tag.originalUrl());
    }

    @Test
    public void testAbsoluteLocalDirIsScanned() {
        assertNotNull(images.get(null, "/local/images/white_1920_1080.png", "small"), "local dir not scanned");
    }
}
