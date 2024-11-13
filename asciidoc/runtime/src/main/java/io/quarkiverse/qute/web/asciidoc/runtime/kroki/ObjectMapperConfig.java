package io.quarkiverse.qute.web.asciidoc.runtime.kroki;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.quarkus.jackson.ObjectMapperCustomizer;

@ApplicationScoped
public class ObjectMapperConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
