package com.abnamro.recipe.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.abnamro.recipe.model.DietaryProfile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes a {@link DietaryProfile} to a JSON string on write. Used on databases
 * without a native Postgres {@code jsonb} type (e.g. the H2 test database, where
 * the column is a plain {@code VARCHAR}); on PostgreSQL the
 * {@link DietaryProfileWritingConverter} (which emits a {@code jsonb} PGobject) is
 * registered instead. See {@link JdbcConfig}.
 */
@WritingConverter
public class DietaryProfileToJsonStringConverter implements Converter<DietaryProfile, String> {

    private final ObjectMapper objectMapper;

    public DietaryProfileToJsonStringConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(DietaryProfile source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize DietaryProfile to JSON", e);
        }
    }
}
