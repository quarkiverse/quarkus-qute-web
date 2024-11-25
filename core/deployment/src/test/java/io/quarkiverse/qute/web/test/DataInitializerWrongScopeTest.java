package io.quarkiverse.qute.web.test;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.RequestScoped;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.qute.web.DataInitializer;
import io.quarkus.test.QuarkusUnitTest;

public class DataInitializerWrongScopeTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClass(WrongInitializer.class)
                    .addAsResource(new StringAsset(
                            "Hello {name ?: 'foo'}!"),
                            "templates/pub/baz.txt"))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @RequestScoped
    public static class WrongInitializer implements DataInitializer {

        @Override
        public void initialize(InitContext ctx) {
        }

    }

}
