package io.quarkiverse.qute.web.image.deployment.items.model;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** What a build did with its images: the summary line, and the per-source lines when verbose. */
public final class ImageStats {

    /** A point in the counts, to describe what one source added. */
    public record Progress(int generated, int reused, long bytes, long millis) {
    }

    private int downloads;
    private long downloadBytes;
    private long downloadMillis;
    private int generated;
    private long generatedBytes;
    private long generatedMillis;
    private int reused;
    private int passedThrough;
    // what this build wrote: a second request for the same variant is not a reuse
    private final Set<String> written = new HashSet<>();

    public void downloaded(long bytes, long millis) {
        downloads++;
        downloadBytes += bytes;
        downloadMillis += millis;
    }

    public void generated(String outputPath, long bytes, long millis) {
        written.add(outputPath);
        generated++;
        generatedBytes += bytes;
        generatedMillis += millis;
    }

    /** A variant found on disk: reused from a previous build, unless this build just wrote it. */
    public void reused(String outputPath) {
        if (!written.contains(outputPath)) {
            reused++;
        }
    }

    public void passedThrough() {
        passedThrough++;
    }

    public Progress progress() {
        return new Progress(generated, reused, generatedBytes, generatedMillis);
    }

    public String summary(int sources) {
        String downloaded = downloads == 0 ? "" : ", %s in %s".formatted(bytes(downloadBytes), millis(downloadMillis));
        String converted = generated == 0 ? "" : " (%s in %s)".formatted(bytes(generatedBytes), millis(generatedMillis));
        return "Images: %d sources (%d downloaded%s), %d variants generated%s, %d reused, %d passed through"
                .formatted(sources, downloads, downloaded, generated, converted, reused, passedThrough);
    }

    /** What one source added between two points, e.g. "6 variants (jpg, webp at 350, 600), 310 KB in 480 ms". */
    public static String converted(Progress before, Progress after, String formats, String widths) {
        int generated = after.generated() - before.generated();
        int reused = after.reused() - before.reused();
        String outcome = generated == 0 ? "all reused"
                : bytes(after.bytes() - before.bytes()) + " in " + millis(after.millis() - before.millis())
                        + (reused == 0 ? "" : ", " + reused + " reused");
        return "%d variants (%s at %s), %s".formatted(generated + reused, formats, widths, outcome);
    }

    public static String bytes(long bytes) {
        return bytes < 1024 * 1024 ? Math.max(1, (bytes + 1023) / 1024) + " KB"
                : String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    public static String millis(long millis) {
        return millis < 1000 ? millis + " ms" : String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
