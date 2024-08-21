package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.deployment.QuteWebTemplateBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.builder.BuildStepBuilder;
import io.quarkus.test.QuarkusUnitTest;

public class LinkedTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "Hello {name ?: 'world'}!"),
                "templates/pub/hello.txt");
        root.addAsResource(new StringAsset(
                "Linked Hello {name ?: 'world'}!"),
                "templates/linked.txt");
    }).addBuildChainCustomizer(buildChainBuilder -> {
        final BuildStepBuilder stepBuilder = buildChainBuilder.addBuildStep(new BuildStep() {
            @Override
            public void execute(BuildContext context) {
                context.produce(new QuteWebTemplateBuildItem("pub/hello.txt", "/foo/bar"));
                context.produce(new QuteWebTemplateBuildItem("pub/hello.txt", "/"));
                context.produce(new QuteWebTemplateBuildItem("linked.txt", "/hello.txt"));
            }
        });
        stepBuilder.produces(QuteWebTemplateBuildItem.class).build();
    });

    @Test
    public void testFixedLink() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));
        given()
                .when().get("/foo/bar")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));
        given()
                .when().get("/hello.txt")
                .then()
                .statusCode(200)
                .body(containsString("Linked Hello world!"));

    }
}
