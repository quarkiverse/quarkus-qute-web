package io.quarkiverse.qute.web.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.builder.BuildStepBuilder;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;

public class BuildItemTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withEmptyApplication()
            .addBuildChainCustomizer(buildChainBuilder -> {
                final BuildStepBuilder stepBuilder = buildChainBuilder.addBuildStep(new BuildStep() {
                    @Override
                    public void execute(BuildContext context) {
                        context.produce(TemplatePathBuildItem.builder()
                                .path("pub/hello.txt")
                                .content("Hello {name ?: 'world'}!")
                                .extensionInfo("qute-web")
                                .build());
                    }
                });
                stepBuilder.produces(TemplatePathBuildItem.class).build();
            });

    @Test
    public void testBuildItemTemplate() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));
        given()
                .when().get("/hello.txt")
                .then()
                .statusCode(200)
                .body(containsString("Hello world!"));
    }
}
