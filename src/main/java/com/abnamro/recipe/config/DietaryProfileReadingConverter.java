package com.abnamro.recipe.config;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.abnamro.recipe.model.DietaryProfile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Deserializes a Postgres {@code jsonb} column value back into a
 * {@link DietaryProfile} value object on read.
 */
@ReadingConverter
public class DietaryProfileReadingConverter implements Converter<PGobject, DietaryProfile> {

    private final ObjectMapper objectMapper;

    public DietaryProfileReadingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DietaryProfile convert(PGobject source) {
        String value = source.getValue();
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, DietaryProfile.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize DietaryProfile from JSON", e);
        }
    }
}
