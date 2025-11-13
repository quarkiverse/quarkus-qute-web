package io.quarkiverse.qute.web.image.deployment.items;

import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.qute.SectionNode;

/**
 * Represents a list of Qute templates to scan for image tags
 */
public final class QuteImageTemplateToScanBuildItem extends MultiBuildItem {

    // this is used to locate images relative to the template
    // it may be the source file or a target file as soon as the images are relative to it
    public final URI location;
    public final Collection<ImageTagSection> sectionNodes;
    public final String id;

    public QuteImageTemplateToScanBuildItem(URI location, List<ImageTagSection> sectionNodes, String id) {
        this.location = location;
        // We make sure absolute path are processed before
        // so that we use them if relative are targeting a static image
        this.sectionNodes = sectionNodes.stream().sorted(Comparator.comparing(ImageTagSection::isAbsolute)).toList();
        this.id = id;
    }

    public record ImageTagSection(SectionNode section, String fileParam) {
        public boolean isAbsolute() {
            return fileParam.startsWith("/");
        }
    }

}
