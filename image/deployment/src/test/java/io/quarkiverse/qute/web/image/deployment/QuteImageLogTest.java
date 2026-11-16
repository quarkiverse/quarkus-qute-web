package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The build says what it does: one line when it starts converting, a summary, and with
 * {@code quarkus.qute.image.verbose} one line per converted source and preset.
 */
public class QuteImageLogTest {

    // a fresh build dir, so that variants are generated and not reused
    static final Path BUILD_DIR = Path.of("target/qute-image-log").toAbsolutePath();

    static {
        try (Stream<Path> files = Files.exists(BUILD_DIR) ? Files.walk(BUILD_DIR) : Stream.<Path> empty()) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.verbose", "true")
            .overrideConfigKey("quarkus.qute.image.build-dir", BUILD_DIR.toString())
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(context -> context.produce(ImagesDirBuildItem.of("web")))
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("web"))
            .setLogRecordPredicate(record -> record.getLoggerName().startsWith("io.quarkiverse.qute.web.image"))
            .assertLogRecords(QuteImageLogTest::assertLogs);

    static void assertLogs(List<LogRecord> records) {
        assertTrue(info(records, "Converting images (build dir target/qute-image-log)"),
                "no start line in " + messages(records));
        assertTrue(records.stream().anyMatch(r -> isInfo(r) && message(r).startsWith("Converted /static/images/")
                && message(r).contains("with small: 2 variants (jpg at 320, 640), ") && message(r).contains(" KB in ")),
                "no converted line in " + messages(records));
        assertTrue(records.stream().anyMatch(r -> isInfo(r) && message(r).startsWith("Images: ")
                && message(r).contains("variants generated (")), "no summary in " + messages(records));
    }

    /** The formatted message: a record of a jboss-logging {@code infof} call carries the format and its parameters. */
    static String message(LogRecord record) {
        return record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage();
    }

    static boolean info(List<LogRecord> records, String prefix) {
        return records.stream().anyMatch(r -> isInfo(r) && message(r).startsWith(prefix));
    }

    /** By value: jboss-logmanager has its own Level instances. */
    static boolean isInfo(LogRecord record) {
        return record.getLevel().intValue() == Level.INFO.intValue();
    }

    static List<String> messages(List<LogRecord> records) {
        return records.stream().filter(r -> r.getLevel().intValue() >= Level.INFO.intValue()).map(QuteImageLogTest::message)
                .toList();
    }

    @Test
    public void testLogs() {
        // the assertions are in assertLogRecords
    }
}
