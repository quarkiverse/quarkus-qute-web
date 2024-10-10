package io.quarkiverse.qute.web.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.Template;

@Path("/home")
public class HomePage {

    @Inject
    Template home;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String home() {
        return home.data("name", "DevNation", "markdown", "## Quarkus is amazing!").render();
    }
}
