package io.quarkiverse.qute.web.markdown.test;

import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class QuarkusMarkdownConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addAsResource(new StringAsset("{data.markdownify}"), "templates/foo.txt")
                            .addAsResource(new StringAsset("{data.mdToHtml}"), "templates/bar.txt")
                            .addAsResource(new StringAsset("quarkus.qute.web.markdown.heading-anchor.enabled=false\n" +
                                    "quarkus.qute.web.markdown.autolink.enabled=false"), "application.properties"));

    @Inject
    Instance<Extension> extensions;

    @Test
    void shouldNotHaveExtensionInstances() {
        List<Extension> list = extensions.stream().toList();
        Assertions.assertEquals(0, list.size());
    }
}
