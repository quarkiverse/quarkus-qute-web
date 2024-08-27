package io.quarkiverse.qute.web.test;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.deployment.QuteWebTemplateBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.builder.BuildStepBuilder;
import io.quarkus.test.QuarkusUnitTest;

public class LinkedDuplicateTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().withApplicationRoot(root -> {
        root.addAsResource(new StringAsset(
                "Hello {name ?: 'world'}!"),
                "templates/pub/hello.txt");
    }).addBuildChainCustomizer(buildChainBuilder -> {
        final BuildStepBuilder stepBuilder = buildChainBuilder.addBuildStep(new BuildStep() {
            @Override
            public void execute(BuildContext context) {
                context.produce(new QuteWebTemplateBuildItem("pub/hello.txt", "/foo/bar"));
                context.produce(new QuteWebTemplateBuildItem("pub/hello.txt", "/foo/bar"));
            }
        });
        stepBuilder.produces(QuteWebTemplateBuildItem.class).build();
    }).assertException(throwable -> {
        Assertions.assertInstanceOf(IllegalStateException.class, throwable);
    });

    @Test
    public void testDuplicateLink() {

    }
}
