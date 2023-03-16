package io.quarkiverse.quteserverpages.runtime;

import java.util.Set;
import java.util.function.Consumer;

import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class QspRecorder {

    private final HttpBuildTimeConfig httpBuildTimeConfig;

    public QspRecorder(HttpBuildTimeConfig httpBuildTimeConfig) {
        this.httpBuildTimeConfig = httpBuildTimeConfig;
    }

    public Consumer<Route> initializeRoute() {
        return new Consumer<Route>() {

            @Override
            public void accept(Route r) {
                r.method(HttpMethod.GET);
            }
        };
    }

    public Handler<RoutingContext> handler(String rootPath, Set<String> templatePaths) {
        return new QspHandler(rootPath, templatePaths, httpBuildTimeConfig);
    }
}
