package io.quarkiverse.qute.web.runtime;

import static io.quarkus.qute.TemplateExtension.ANY;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.http.HttpServerRequest;

@Vetoed
@TemplateExtension(namespace = "http")
public class QuteWebExtensions {

    static HttpServerRequest request() {
        return Arc.container().instance(HttpServerRequest.class).get();
    }

    static String param(String name) {
        return request().getParam(name);
    }

    static String param(String name, String defaultValue) {
        return request().getParam(name, defaultValue);
    }

    static String header(String name) {
        return request().getHeader(name);
    }

    static Headers headers() {
        return Headers.INSTANCE;
    }

    @TemplateExtension(matchName = ANY)
    static String headers(Headers headers, String name) {
        // Note that http headers are case-insensitive
        return request().getHeader(name);
    }

    public enum Headers {
        INSTANCE
    }

}
