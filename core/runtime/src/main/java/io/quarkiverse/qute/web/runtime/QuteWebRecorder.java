package io.quarkiverse.qute.web.runtime;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class QuteWebRecorder {

    private final RuntimeValue<VertxHttpBuildTimeConfig> httpConfig;
    private final RuntimeValue<QuteWebBuildTimeConfig> quteWebConfig;

    public QuteWebRecorder(RuntimeValue<VertxHttpBuildTimeConfig> httpConfig,
            RuntimeValue<QuteWebBuildTimeConfig> quteWebConfig) {
        this.httpConfig = httpConfig;
        this.quteWebConfig = quteWebConfig;
    }

    public Consumer<Route> initializeRoute() {
        return new Consumer<Route>() {

            @Override
            public void accept(Route r) {
                r.method(HttpMethod.GET);
                r.order(quteWebConfig.getValue().routeOrder());
            }
        };
    }

    public Handler<RoutingContext> handler(String rootPath,
            Set<String> templatePaths, Map<String, String> templateLinks) {
        return new QuteWebHandler(rootPath, quteWebConfig.getValue().publicDir(), templatePaths, templateLinks,
                httpConfig.getValue());
    }
}
