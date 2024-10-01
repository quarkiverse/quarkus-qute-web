package io.quarkiverse.qute.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import io.quarkus.qute.TemplateInstance;
import io.vertx.core.http.HttpServerRequest;

/**
 * This component can be used to initialize template data for specific pages, i.e. to set the data and attributes of a template
 * instance
 * for a given path.
 * <p>
 * Implementations must be {@link Singleton} or {@link ApplicationScoped} CDI beans.
 */
public interface DataInitializer {

    /**
     *
     * @param context
     */
    void initialize(InitContext context);

    /**
     *
     * @param path The real template path, e.g. {@code pub/item.html} for the requested path {@code /item.html}
     * @param templateInstance
     * @param request
     */
    record InitContext(String path, TemplateInstance templateInstance, HttpServerRequest request) {
    }

}
