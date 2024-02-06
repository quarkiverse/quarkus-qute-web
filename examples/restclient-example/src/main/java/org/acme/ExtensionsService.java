package org.acme;

import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Named("extensions")
@Singleton
public class ExtensionsService {

    @RestClient
    ExtensionsClient client;

    public Uni<List<Extension>> getAll(String keyword) {
        return client.getAll().map(all -> {
            Stream<Extension> stream = all.stream();
            if (keyword != null) {
                stream = stream.filter(e -> e.keywords().contains(keyword));
            }
            return stream.sorted(Comparator.comparing(e -> e.name().toLowerCase())).collect(toList());
        });
    }

}
