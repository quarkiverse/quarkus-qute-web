package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageRef;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;

public class QuteImageTemplateToScanBuildItemTest {

    @Test
    public void testAbsolutePathsSortedBeforeRelative() {
        List<ImageRef> refs = List.of(
                new ImageRef("test", "relative.png", "default", PresetConfig.DEFAULT),
                new ImageRef("test", "/images/absolute.png", "default", PresetConfig.DEFAULT),
                new ImageRef("test", "another-relative.png", "default", PresetConfig.DEFAULT),
                new ImageRef("test", "/images/another-absolute.png", "default", PresetConfig.DEFAULT));

        QuteImageTemplateToScanBuildItem item = new QuteImageTemplateToScanBuildItem(
                URI.create("templates/test.html"), refs, "test");

        List<ImageRef> sorted = List.copyOf(item.imageRefs);

        assertThat("first entry should be absolute", sorted.get(0).isAbsolute(), is(true));
        assertThat("second entry should be absolute", sorted.get(1).isAbsolute(), is(true));
        assertThat("third entry should be relative", sorted.get(2).isAbsolute(), is(false));
        assertThat("fourth entry should be relative", sorted.get(3).isAbsolute(), is(false));
    }
}
