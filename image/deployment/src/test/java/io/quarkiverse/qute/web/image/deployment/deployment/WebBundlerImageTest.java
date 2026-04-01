package io.quarkiverse.qute.web.image.deployment.deployment;

import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.image.spi.items.ImageSourceDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.WhitelistDirBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerImageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(new Customiser())
            .setForcedDependencies(
                    List.of(new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("roq")
                    .addAsResource("web"));

    public static class MyBuildStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            try {
                byte[] bytes = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("/roq/index.html").readAllBytes();
                context.produce(TemplatePathBuildItem.builder()
                        .path("index.html")
                        .source(URI.create("target/test-classes/roq/index.html"))
                        .content(new String(bytes, StandardCharsets.UTF_8))
                        .extensionInfo("Roq")
                        .build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            context.produce(new ImageSourceDirBuildItem(java.nio.file.Path.of("/web"), true, new Mapper()));
            AtomicReference<java.nio.file.Path> root = new AtomicReference<>();
            QuarkusClassLoader.visitRuntimeResources("web/static/images/white_1920_1080.png", p -> {
                root.set(p.getRoot());
            });
            context.produce(
                    new WhitelistDirBuildItem(root.get()));
            context.produce(
                    new ImageSourceDirBuildItem(java.nio.file.Path.of("target/test-classes/roq-public"), false, null));
        }

        private static class Mapper implements Function<String, String> {
            @Override
            public String apply(String s) {
                return s.replace('é', '-').toLowerCase();
            }
        }
    }

    public static class Customiser implements Consumer<BuildChainBuilder> {

        @Override
        public void accept(BuildChainBuilder buildChainBuilder) {
            buildChainBuilder.addBuildStep(new MyBuildStep())
                    .produces(WhitelistDirBuildItem.class)
                    .produces(TemplatePathBuildItem.class)
                    .produces(ImageSourceDirBuildItem.class)
                    .build();
        }
    }

    @Test
    public void testImageQuteWeb() {

        RestAssured.given()
                .get("/images.html")
                .then()
                .statusCode(200)
                .log()
                .body()
                .body(equalTo(
                        """
                                <img src="/static/images/white_1920_1080.png" srcset="/static/processed-images/1b139664/white_1920_1080_640.png 640w, /static/processed-images/1b139664/white_1920_1080_1024.png 1024w"/>
                                <img src="/static/images/white_1920_1080.png" srcset="/static/processed-images/1b139664/white_1920_1080_640.png 640w, /static/processed-images/1b139664/white_1920_1080_1024.png 1024w"/>
                                """
                                .replaceAll("\\v", "")));
        RestAssured.given()
                .get("/static/images/white_1920_1080.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/static/processed-images/1b139664/white_1920_1080_640.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/static/processed-images/1b139664/white_1920_1080_1024.png")
                .then()
                .statusCode(200);
    }

    @Test
    public void testImageRunTime() {

        RestAssured.given()
                .get("/rest")
                .then()
                .statusCode(200)
                .log().body()
                // FIXME There should actually be a newline in there but for some reason it gets removed
                .body(equalTo(
                        """
                                <img src="/static/processed-images/1b139664/white_1920_1080_original.png" srcset="/static/processed-images/1b139664/white_1920_1080_640.png 640w, /static/processed-images/1b139664/white_1920_1080_1024.png 1024w"/>
                                <img src="/static/images/white_1920_1080.png" srcset="/static/processed-images/1b139664/white_1920_1080_640.png 640w, /static/processed-images/1b139664/white_1920_1080_1024.png 1024w"/>
                                <img src="/static/images/fo-_1920_1081.png" srcset="/static/processed-images/1b139664/fo-_1920_1081_640.png 640w, /static/processed-images/1b139664/fo-_1920_1081_1024.png 1024w"/>
                                <img src="/static/processed-images/1b139664/Foé_1920_1080_original.png" srcset="/static/processed-images/1b139664/Foé_1920_1080_640.png 640w, /static/processed-images/1b139664/Foé_1920_1080_1024.png 1024w"/>
                                <img src="/static/processed-images/1b139664/Foé_1920_1081_original.png" srcset="/static/processed-images/1b139664/Foé_1920_1081_640.png 640w, /static/processed-images/1b139664/Foé_1920_1081_1024.png 1024w"/>
                                """
                                .replaceAll("\\v", "")));

        String[] urls = {
                "/static/processed-images/1b139664/white_1920_1080_original.png",
                "/static/processed-images/1b139664/white_1920_1080_640.png",
                "/static/processed-images/1b139664/white_1920_1080_1024.png",

                "/static/images/white_1920_1080.png",
                "/static/processed-images/1b139664/white_1920_1080_640.png",
                "/static/processed-images/1b139664/white_1920_1080_1024.png",

                "/static/processed-images/1b139664/Foé_1920_1080_original.png",
                "/static/processed-images/1b139664/Foé_1920_1080_640.png",
                "/static/processed-images/1b139664/Foé_1920_1080_1024.png",

                "/static/processed-images/1b139664/Foé_1920_1081_original.png",
                "/static/processed-images/1b139664/Foé_1920_1081_640.png",
                "/static/processed-images/1b139664/Foé_1920_1081_1024.png"
        };
        for (String url : urls) {
            RestAssured.given().get(url).then().statusCode(200);
        }
    }

    @Path("/rest")
    public static class Endpoint {
        @Inject
        @Location("index.html")
        Template index;

        @GET
        public String get() {
            return index.instance().render();
        }
    }
}
