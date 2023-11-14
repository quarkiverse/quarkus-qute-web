package io.quarkiverse.qute.web.runtime;

import java.util.Set;
import java.util.function.Consumer;

import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class QuteWebRecorder {

    private final HttpBuildTimeConfig httpConfig;
    private final QuteWebBuildTimeConfig quteWebConfig;

    public QuteWebRecorder(HttpBuildTimeConfig httpConfig, QuteWebBuildTimeConfig quteWebConfig) {
        this.httpConfig = httpConfig;
        this.quteWebConfig = quteWebConfig;
    }

    public Consumer<Route> initializeRoute() {
        return new Consumer<Route>() {

            @Override
            public void accept(Route r) {
                r.method(HttpMethod.GET);
                r.order(quteWebConfig.routeOrder());
            }
        };
    }

    public Handler<RoutingContext> handler(String rootPath, Set<String> templatePaths) {
        return new QuteWebHandler(rootPath, quteWebConfig.publicDir(), templatePaths, httpConfig);
    }
}
