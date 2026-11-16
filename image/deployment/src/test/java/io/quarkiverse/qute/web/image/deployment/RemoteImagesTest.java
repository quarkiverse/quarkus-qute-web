package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.qute.web.image.converter.ImageIOConverter;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageStats;

/**
 * Remote originals are downloaded once into the cache, named by url digest with the extension of their real
 * format, the pipeline sees them under the url's last segment, and untrusted bytes are refused before anything is
 * decoded: too large, not an image, or too many pixels.
 */
public class RemoteImagesTest {

    /** The files this test serves, under target/test-classes, deleted after each test. */
    private final List<Path> served = new ArrayList<>();
    private final ImageStats stats = new ImageStats();

    @AfterEach
    void deleteServedFiles() throws IOException {
        for (Path file : served) {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void testDownloadedOnceAndNamedByFormat(@TempDir Path buildDir) throws IOException {
        // a served file of our own, so that it can be deleted after the first fetch
        String url = serve("once", Files.readAllBytes(FixtureServer.ROOT.resolve("remote/photo")));
        RemoteImages remote = remote(buildDir);

        Path cached = remote.fetch(url);
        // the full digest of the url: a short one could let two urls share one cache entry
        assertTrue(cached.getFileName().toString().matches("[0-9a-f]{40}\\.png"), cached.toString());
        assertEquals(buildDir.resolve("remote"), cached.getParent());
        assertEquals(served.get(0).getFileName() + ".png", RemoteImages.fileName(url, cached));

        Files.delete(served.get(0));
        assertEquals(cached, remote.fetch(url), "second fetch should come from the cache");
        assertTrue(stats.summary(1).startsWith("Images: 1 sources (1 downloaded, "), stats.summary(1));
        assertEquals(cached, remote(buildDir).fetch(url), "a later build should find the cache on disk");
    }

    @Test
    public void testFileNames() {
        Path cached = Path.of("remote/0123abcd.png");
        assertEquals("photo.png", RemoteImages.fileName("https://host/images/photo.jpg?w=1200", cached));
        assertEquals("photo-1457365050282.png",
                RemoteImages.fileName("https://images.unsplash.com/photo-1457365050282?w=1200&fm=jpg", cached));
        assertEquals("0123abcd.png", RemoteImages.fileName("https://host/", cached));
        assertEquals("0123abcd.png", RemoteImages.fileName("https://host", cached));
    }

    /** The timeout bounds the whole download, not only the headers: a host trickling bytes cannot hang the build. */
    @Test
    public void testBodyReadIsBoundedByTheTimeout(@TempDir Path buildDir) {
        RemoteImages remote = new RemoteImages(buildDir, new ImageIOConverter(),
                new RemoteImages.Options(Duration.ofMillis(200), true, false), stats);
        long start = System.currentTimeMillis();
        assertThrows(IOException.class, () -> remote.fetch(FixtureServer.url(FixtureServer.SLOW)));
        assertTrue(System.currentTimeMillis() - start < 1_000, "the download should give up after the timeout");
    }

    /**
     * A download whose header reads fine but whose bytes cannot be decoded is refused and left out of the cache:
     * otherwise every later build would fail on the cached file until someone deletes it by hand.
     */
    @Test
    public void testUndecodableDownloadIsNotCached(@TempDir Path buildDir) throws IOException {
        byte[] truncated = Arrays.copyOf(Files.readAllBytes(FixtureServer.ROOT.resolve("remote/photo")), 100);
        String url = serve("truncated", truncated);
        assertThrows(IOException.class, () -> remote(buildDir).fetch(url));
        try (Stream<Path> files = Files.list(buildDir.resolve("remote"))) {
            assertEquals(List.of(), files.toList(), "nothing should be cached");
        }
    }

    @Test
    public void testMissingUrlFails(@TempDir Path buildDir) {
        assertEquals("HTTP 404", fetchFailure(buildDir, FixtureServer.url("remote/missing")));
    }

    @Test
    public void testInvalidUrlFails(@TempDir Path buildDir) {
        assertEquals("invalid url", fetchFailure(buildDir, "http://host/a photo.jpg"));
    }

    @Test
    public void testNotAnImageIsRefused(@TempDir Path buildDir) throws IOException {
        String url = serve("text", "<!DOCTYPE html><html>not found</html>".getBytes(StandardCharsets.UTF_8));
        assertEquals("No ImageIO reader accepts this image", fetchFailure(buildDir, url));
    }

    @Test
    public void testTooLargeIsRefused(@TempDir Path buildDir) throws IOException {
        byte[] bytes = new byte[RemoteImages.MAX_BYTES + 1];
        // the size is checked before the format
        assertEquals("larger than 20 MB", fetchFailure(buildDir, serve("large", bytes)));
    }

    @Test
    public void testTooManyPixelsAreRefused(@TempDir Path buildDir) throws IOException {
        // 56 megapixels of one bit each: a small file that would decode to hundreds of megabytes
        BufferedImage bomb = new BufferedImage(8000, 7000, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(bomb, "png", bytes);
        assertEquals("8000x7000 exceeds 50 megapixels", fetchFailure(buildDir, serve("bomb", bytes.toByteArray())));
    }

    /** A vector is an svg document, not any page that happens to contain an inline svg (a soft 404 with a logo). */
    @Test
    public void testOnlyAnSvgDocumentIsAVector(@TempDir Path buildDir) throws IOException {
        String page = serve("page", "<!DOCTYPE html><html><body><svg viewBox='0 0 1 1'/>not found</body></html>"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals("No ImageIO reader accepts this image", fetchFailure(buildDir, page));

        String svg = serve("vector", ("﻿<?xml version=\"1.0\"?>\n<!-- a comment -->\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"/>").getBytes(StandardCharsets.UTF_8));
        assertTrue(remote(buildDir).fetch(svg).getFileName().toString().endsWith(".svg"));
    }

    @Test
    public void testPlainHttpIsRefusedByDefault(@TempDir Path buildDir) {
        RemoteImages remote = new RemoteImages(buildDir, new ImageIOConverter(),
                new RemoteImages.Options(Duration.ofSeconds(5), false, true), new ImageStats());
        IOException e = assertThrows(IOException.class, () -> remote.fetch(FixtureServer.url("remote/photo")));
        assertEquals("plain http is not allowed, set quarkus.qute.image.remote.allow-http=true", e.getMessage());
    }

    /** The fixture server is plain http: allowed for the tests. */
    private RemoteImages remote(Path buildDir) {
        return new RemoteImages(buildDir, new ImageIOConverter(), new RemoteImages.Options(Duration.ofSeconds(5), true, true),
                stats);
    }

    private String fetchFailure(Path buildDir, String url) {
        return assertThrows(IOException.class, () -> remote(buildDir).fetch(url)).getMessage();
    }

    private String serve(String name, byte[] bytes) throws IOException {
        Path file = FixtureServer.ROOT.resolve("remote/" + name + "-" + System.nanoTime());
        Files.write(file, bytes);
        served.add(file);
        return FixtureServer.url("remote/" + file.getFileName());
    }
}
