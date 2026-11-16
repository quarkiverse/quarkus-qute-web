package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * A local image whose header reads but whose bytes do not decode (a truncated file) fails the build naming the
 * image, the preset and the variant, not just the decoder's message.
 */
public class QuteImageCorruptSourceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new ByteArrayAsset(truncatedPng()), "META-INF/resources/images/truncated.png")
                    .addAsResource(new StringAsset("{#img '/images/truncated.png' preset='small'/}"),
                            "web/templates/pub/corrupt.html"))
            .assertException(t -> {
                String messages = QuteImageToProcessUnknownPresetTest.messages(t);
                assertTrue(messages.contains("Failed to convert image '/images/truncated.png' to 320px jpg (preset 'small')"),
                        messages);
                assertTrue(messages.contains("ImageIO cannot read this image"), messages);
            });

    /** The first hundred bytes of a real png: a valid header, no pixel data. */
    static byte[] truncatedPng() {
        try (InputStream in = QuteImageCorruptSourceTest.class.getClassLoader()
                .getResourceAsStream("web/static/images/quadrants_400_200.png")) {
            return Arrays.copyOf(in.readAllBytes(), 100);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testCorruptSourceFailsTheBuildNamingTheImage() {
        // the assertion is in assertException
    }
}
