package io.quarkiverse.qute.web.it;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@Named("foo")
@ApplicationScoped
public class MyBean {

    public List<String> names() {
        return List.of("Joe", "Violet", "Omaha");
    }

}
