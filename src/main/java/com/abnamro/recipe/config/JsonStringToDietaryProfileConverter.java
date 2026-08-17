package com.abnamro.recipe.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.abnamro.recipe.model.DietaryProfile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Deserializes a JSON string column value back into a {@link DietaryProfile} on
 * read. The counterpart to {@link DietaryProfileToJsonStringConverter}, used on
 * databases without a native Postgres {@code jsonb} type (e.g. H2). See
 * {@link JdbcConfig}.
 */
@ReadingConverter
public class JsonStringToDietaryProfileConverter implements Converter<String, DietaryProfile> {

    private final ObjectMapper objectMapper;

    public JsonStringToDietaryProfileConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DietaryProfile convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(source, DietaryProfile.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize DietaryProfile from JSON", e);
        }
    }
}
