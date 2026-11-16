package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;

/**
 * The widths a preset produces for a source: a width larger than the source is replaced by the source width, once.
 */
public class ImageUtilsEffectiveWidthsTest {

    @Test
    public void testWidthsBelowTheSourceAreKept() {
        assertThat(ImageUtils.effectiveWidths(List.of(320, 640, 1024), 2000), contains(320, 640, 1024));
    }

    @Test
    public void testWidthsAboveTheSourceBecomeTheSourceWidthOnce() {
        assertThat(ImageUtils.effectiveWidths(List.of(320, 640, 1024), 400), contains(320, 400));
    }

    @Test
    public void testASourceNarrowerThanEveryWidthYieldsItsOwnWidth() {
        assertThat(ImageUtils.effectiveWidths(List.of(1200), 800), contains(800));
    }

    @Test
    public void testASourceExactlyAtAWidthIsNotDuplicated() {
        assertThat(ImageUtils.effectiveWidths(List.of(800, 1200), 800), contains(800));
    }

    @Test
    public void testUnknownSourceWidthKeepsThePresetWidths() {
        assertThat(ImageUtils.effectiveWidths(List.of(1024, 320), 0), contains(320, 1024));
    }

    @Test
    public void testNoWidths() {
        assertThat(ImageUtils.effectiveWidths(List.of(), 800), empty());
        assertThat(ImageUtils.effectiveWidths(null, 800), empty());
    }
}
