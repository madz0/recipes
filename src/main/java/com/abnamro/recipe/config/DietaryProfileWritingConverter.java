package com.abnamro.recipe.config;

import java.sql.SQLException;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.abnamro.recipe.model.DietaryProfile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes a {@link DietaryProfile} value object into a Postgres {@code jsonb}
 * column value on write.
 */
@WritingConverter
public class DietaryProfileWritingConverter implements Converter<DietaryProfile, PGobject> {

    private final ObjectMapper objectMapper;

    public DietaryProfileWritingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PGobject convert(DietaryProfile source) {
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(source));
            return pgObject;
        } catch (SQLException | JacksonException e) {
            throw new IllegalStateException("Failed to serialize DietaryProfile to JSON", e);
        }
    }
}
