package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.ImageOptions;

/**
 * The persistent build dir reuses a variant only when its file is complete, and a variant is never written under
 * its final name until it is complete.
 */
public class GeneratedVariantReuseTest {

    static final byte[] BYTES = { 1, 2, 3 };

    /** Writes the given bytes wherever it is told, like a real converter does. */
    static final ImageConverter STUB = new ImageConverter() {
        @Override
        public ImageInfo readInfo(byte[] sourceImage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path process(byte[] sourceImage, ImageOptions options, Path outputPath) {
            try {
                Files.createDirectories(outputPath.getParent());
                return Files.write(outputPath, sourceImage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String id() {
            return "stub";
        }
    };

    @Test
    public void testMissingOrEmptyVariantIsNotComplete(@TempDir Path dir) throws IOException {
        Path missing = dir.resolve("missing.jpg");
        Path empty = Files.write(dir.resolve("empty.jpg"), new byte[0]);
        Path full = Files.write(dir.resolve("full.jpg"), BYTES);
        assertFalse(QuteImageProcessor.isComplete(missing));
        assertFalse(QuteImageProcessor.isComplete(empty), "an interrupted write must not be reused");
        assertTrue(QuteImageProcessor.isComplete(full));
    }

    @Test
    public void testAtomicWriteReplacesTruncatedFileAndLeavesNoTemp(@TempDir Path dir) throws IOException {
        Path target = Files.write(Files.createDirectories(dir.resolve("gen")).resolve("photo-640-h.jpg"),
                new byte[0]);
        QuteImageProcessor.writeAtomically(STUB, BYTES, null, target);
        assertArrayEquals(BYTES, Files.readAllBytes(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertTrue(files.allMatch(target::equals), "no temp file left next to the variant");
        }
    }
}
