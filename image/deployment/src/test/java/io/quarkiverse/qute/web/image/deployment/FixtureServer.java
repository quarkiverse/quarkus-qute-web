package io.quarkiverse.qute.web.image.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

/**
 * One http server per JVM serving {@code target/test-classes} at {@code /} (404 when the file is missing), for the
 * tests that download remote sources. {@link #SLOW} answers at once, then sends one byte every 50 ms for one second,
 * for the timeout test. The port travels in a system property: QuarkusUnitTest loads test classes twice,
 * once per classloader, and both sides must agree on the url.
 */
final class FixtureServer {

    static final Path ROOT = Path.of("target/test-classes").toAbsolutePath();
    static final String SLOW = "slow";
    private static final String BASE_PROPERTY = FixtureServer.class.getName() + ".base";

    private FixtureServer() {
    }

    /** The url of a file under target/test-classes, starting the server on first use. */
    static synchronized String url(String path) {
        if (System.getProperty(BASE_PROPERTY) == null) {
            start();
        }
        return System.getProperty(BASE_PROPERTY) + "/" + path;
    }

    private static void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/" + SLOW, exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try (var body = exchange.getResponseBody()) {
                    for (int i = 0; i < 20; i++) {
                        body.write(0);
                        body.flush();
                        Thread.sleep(50);
                    }
                } catch (InterruptedException | IOException ignored) {
                    // the client gave up
                }
            });
            server.createContext("/", exchange -> {
                Path file = ROOT.resolve(exchange.getRequestURI().getPath().substring(1));
                if (Files.isRegularFile(file)) {
                    byte[] bytes = Files.readAllBytes(file);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
                exchange.close();
            });
            server.start();
            System.setProperty(BASE_PROPERTY, "http://localhost:" + server.getAddress().getPort());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
