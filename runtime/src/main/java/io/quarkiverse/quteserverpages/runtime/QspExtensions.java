package io.quarkiverse.quteserverpages.runtime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.http.HttpServerRequest;

@Vetoed
@TemplateExtension(namespace = "qsp")
public class QspExtensions {

    static HttpServerRequest request() {
        return Arc.container().instance(HttpServerRequest.class).get();
    }

    static String param(String name) {
        return request().getParam(name);
    }

    static String param(String name, String defaultValue) {
        return request().getParam(name, defaultValue);
    }

}
