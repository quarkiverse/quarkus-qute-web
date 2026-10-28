package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageTemplateToScanBuildItem.ImageTagSection;
import io.quarkiverse.qute.web.image.runtime.PresetConfig;

public class QuteImageTemplateToScanBuildItemTest {

    @Test
    public void testAbsolutePathsSortedBeforeRelative() {
        List<ImageTagSection> sections = List.of(
                new ImageTagSection(null, "relative.png", "default", PresetConfig.DEFAULT),
                new ImageTagSection(null, "/images/absolute.png", "default", PresetConfig.DEFAULT),
                new ImageTagSection(null, "another-relative.png", "default", PresetConfig.DEFAULT),
                new ImageTagSection(null, "/images/another-absolute.png", "default", PresetConfig.DEFAULT));

        QuteImageTemplateToScanBuildItem item = new QuteImageTemplateToScanBuildItem(
                URI.create("templates/test.html"), sections, "test");

        List<ImageTagSection> sorted = List.copyOf(item.sectionNodes);

        assertThat("first entry should be absolute", sorted.get(0).isAbsolute(), is(true));
        assertThat("second entry should be absolute", sorted.get(1).isAbsolute(), is(true));
        assertThat("third entry should be relative", sorted.get(2).isAbsolute(), is(false));
        assertThat("fourth entry should be relative", sorted.get(3).isAbsolute(), is(false));
    }
}
