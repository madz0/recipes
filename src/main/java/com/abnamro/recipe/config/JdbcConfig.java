package com.abnamro.recipe.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import tools.jackson.databind.ObjectMapper;

/**
 * Registers the custom Spring Data JDBC converters that map the
 * {@code DietaryProfile} value object to and from a Postgres {@code jsonb}
 * column.
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;

    public JdbcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                new DietaryProfileWritingConverter(objectMapper),
                new DietaryProfileReadingConverter(objectMapper)
        );
    }
}
