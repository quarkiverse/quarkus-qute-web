package io.quarkiverse.qute.web.asciidoc.runtime.kroki;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "kroki")
@ApplicationScoped
public interface KrokiClient {

    @GET
    @Path("/{diagramType}/{format}/{encodedPayload}")
    @Consumes(MediaType.APPLICATION_SVG_XML)
    String convert(String diagramType, String format, String encodedPayload);
}
