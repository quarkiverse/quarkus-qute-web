package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.converter.ImageConverter;
import io.quarkiverse.qute.web.image.converter.ImageInfo;
import io.quarkiverse.qute.web.image.converter.UnsupportedFormatException;
import io.quarkiverse.qute.web.image.deployment.items.model.ImageStats;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.tools.stringpaths.StringPaths;

/**
 * Remote sources, downloaded once and cached under the build dir as {@code remote/<url digest>.<ext>}, the
 * extension being the one of the bytes' real format (a url rarely ends with one). The build dir is persistent,
 * so a url is fetched once across builds and dev mode restarts.
 * <p>
 * Remote bytes are untrusted: plain http is refused unless allowed (a proxy could replace the image), a download is
 * capped in size, and its header dimensions are capped before anything is decoded.
 */
final class RemoteImages {

    static final int MAX_BYTES = 20 * 1024 * 1024;
    static final long MAX_PIXELS = 50_000_000L;

    /** What the config says about downloads. */
    record Options(Duration timeout, boolean allowHttp, boolean verbose) {
        static Options from(ImageConfig config) {
            return new Options(config.remote().timeout(), config.remote().allowHttp(), config.verbose());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RemoteImages.class);

    private final Path cacheDir;
    private final ImageConverter converter;
    private final Options options;
    private final ImageStats stats;
    private final HttpClient client;
    private final Map<String, Path> cached = new HashMap<>();

    RemoteImages(Path buildDir, ImageConverter converter, Options options, ImageStats stats) {
        this.cacheDir = buildDir.resolve("remote");
        this.converter = converter;
        this.options = options;
        this.stats = stats;
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(options.timeout()).build();
        try (Stream<Path> files = Files.list(Files.createDirectories(cacheDir))) {
            files.filter(RemoteImages::isComplete)
                    .forEach(file -> cached.put(StringPaths.removeExtension(file.getFileName().toString()), file));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open the remote images cache: " + cacheDir, e);
        }
    }

    /** The cached original of a url, downloaded and checked on first use. */
    Path fetch(String url) throws IOException {
        String digest = ImageUtils.digest(url);
        Path file = cached.get(digest);
        if (file == null) {
            long start = System.currentTimeMillis();
            byte[] bytes = download(url);
            // a vector by its markup, else what the converter reads from the header and decodes in full
            ImageInfo info = isSvg(bytes) ? null : check(bytes);
            file = cacheDir.resolve(digest + "." + (info == null ? "svg" : ImageUtils.extensionFromFormat(info.format())));
            writeAtomically(file, bytes);
            cached.put(digest, file);
            long millis = System.currentTimeMillis() - start;
            stats.downloaded(bytes.length, millis);
            LOGGER.logf(options.verbose() ? Logger.Level.INFO : Logger.Level.DEBUG, "Downloaded %s (%s%s) from %s in %s",
                    fileName(url, file), info == null ? "" : info.width() + "x" + info.height() + ", ",
                    ImageStats.bytes(bytes.length), url, ImageStats.millis(millis));
        } else {
            LOGGER.debugf("Cached original for %s: %s", url, file.getFileName());
        }
        return file;
    }

    /** The name the pipeline sees: the url's last path segment, or its digest when there is none, with the real extension. */
    static String fileName(String url, Path cached) {
        String cachedName = cached.getFileName().toString();
        String segment = StringPaths.removeExtension(StringPaths.fileName(URI.create(url).getPath()));
        return (segment.isBlank() ? StringPaths.removeExtension(cachedName) : segment) + "."
                + StringPaths.fileExtension(cachedName);
    }

    private byte[] download(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid url");
        }
        if (!options.allowHttp() && "http".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("plain http is not allowed, set quarkus.qute.image.remote.allow-http=true");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(options.timeout()).GET().build();
        long deadline = System.currentTimeMillis() + options.timeout().toMillis();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                // the request timeout only covers the headers: the body is read under the same deadline
                return CompletableFuture.supplyAsync(() -> readCapped(body))
                        .get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (TimeoutException e) {
            throw new IOException("timed out after " + ImageStats.millis(options.timeout().toMillis()));
        } catch (ExecutionException e) {
            throw e.getCause() instanceof UncheckedIOException io ? io.getCause() : new IOException(e.getCause());
        }
    }

    private static byte[] readCapped(InputStream body) {
        try {
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("larger than " + MAX_BYTES / (1024 * 1024) + " MB");
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final Pattern SVG_DOCUMENT = Pattern.compile(
            "^\\uFEFF?\\s*(<\\?xml[^>]*>\\s*)?(<!--.*?-->\\s*|<!DOCTYPE[^>]*>\\s*)*<svg[\\s>/]", Pattern.DOTALL);

    /** An svg document, not any page with an inline svg in it: a soft 404 with a logo is not a vector. */
    private static boolean isSvg(byte[] bytes) {
        return SVG_DOCUMENT.matcher(new String(bytes, 0, Math.min(bytes.length, 1024), StandardCharsets.UTF_8)).find();
    }

    /**
     * The header as the converter reads it, refused above {@link #MAX_PIXELS} before anything is decoded, then the
     * whole image decoded once: bytes that only look like an image are refused here, not at every later build.
     */
    private ImageInfo check(byte[] bytes) throws IOException {
        try {
            ImageInfo info = converter.readInfo(bytes);
            if ((long) info.width() * info.height() > MAX_PIXELS) {
                throw new IOException("%dx%d exceeds %d megapixels".formatted(info.width(), info.height(),
                        MAX_PIXELS / 1_000_000));
            }
            converter.decode(bytes);
            return info;
        } catch (UnsupportedFormatException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** Written next to its final name then moved, so that an interrupted build leaves no partial original. */
    private static void writeAtomically(Path file, byte[] bytes) throws IOException {
        Path tmp = file.resolveSibling("." + System.nanoTime() + "-" + file.getFileName());
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean isComplete(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0 && !file.getFileName().toString().startsWith(".");
        } catch (IOException e) {
            return false;
        }
    }
}
