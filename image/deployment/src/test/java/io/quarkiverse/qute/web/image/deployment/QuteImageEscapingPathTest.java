package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * A relative source stays under the directory of its template. One that escapes it fails the build saying so,
 * even when the file exists: "does not exist" would send the author to check a file that is there.
 */
public class QuteImageEscapingPathTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web/static")
                    .addAsResource(new StringAsset("{#img '../../static/images/white_1920_1080.png'/}"),
                            "web/templates/pub/escaping.html"))
            .assertException(t -> {
                String messages = QuteImageToProcessUnknownPresetTest.messages(t);
                assertTrue(messages.contains("'../../static/images/white_1920_1080.png' in template 'pub/escaping.html'"
                        + " escapes the template directory"), messages);
            });

    @Test
    public void testEscapingPathFailsTheBuild() {
        // the assertion is in assertException
    }
}
