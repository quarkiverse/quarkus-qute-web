package io.quarkiverse.qute.web.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * In strict mode a remote source that cannot be downloaded fails the build, naming the url.
 */
public class QuteImageRemoteStrictTest {

    static final String URL = FixtureServer.url("remote/missing");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.remote.enabled", "true")
            // the fixture server is plain http
            .overrideConfigKey("quarkus.qute.image.remote.allow-http", "true")
            .overrideConfigKey("quarkus.qute.image.strict", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FixtureServer.class)
                    .addAsResource(new StringAsset("{#img '" + URL + "' alt='Remote'/}"),
                            "web/templates/pub/remote-strict.html"))
            .assertException(t -> {
                String message = String.valueOf(t.getMessage());
                assertTrue(message.contains("cannot be converted"), message);
                assertTrue(message.contains(URL), message);
            });

    @Test
    public void testFailedDownloadFailsTheBuild() {
        // the assertion is in assertException
    }
}
