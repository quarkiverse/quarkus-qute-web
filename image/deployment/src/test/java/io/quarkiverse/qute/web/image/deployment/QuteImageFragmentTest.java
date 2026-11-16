package io.quarkiverse.qute.web.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * An image usage inside a {@code {#fragment}} is scanned under the template that holds it, and still found when
 * the fragment is rendered on its own (a fragment reports its own id as template id).
 */
public class QuteImageFragmentTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.qute.image.strict", "true")
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new ProduceStep())
                    .produces(ImagesDirBuildItem.class)
                    .build())
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("web"));

    public static class ProduceStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            context.produce(ImagesDirBuildItem.of("web"));
        }
    }

    @Test
    public void testFragmentFindsItsImages() {
        String body = RestAssured.get("/rest/fragment").then().statusCode(200).extract().asString();
        Document doc = Jsoup.parse(body);
        assertThat(doc.select("img").first().attr("src"), containsString("/static/images/generated/"));
        assertThat(doc.select("#p").text(), containsString("/static/images/generated/"));
    }

    @jakarta.ws.rs.Path("/rest")
    public static class Endpoint {

        @Inject
        @Location("pub/fragmented.html")
        Template fragmented;

        @GET
        @jakarta.ws.rs.Path("/fragment")
        public String getFragment() {
            return fragmented.getFragment("body").render();
        }
    }
}
