package com.abnamro.recipe.model;

/**
 * Value object describing the dietary attributes of a {@link Recipe}.
 * Persisted as a JSONB document in the {@code dietary_profile_attributes}
 * column (see the converters in the config package), e.g.
 * {@code {"gluten": false, "wheat": true, "nut": false}}.
 */
public record DietaryProfile(
        boolean gluten,
        boolean wheat,
        boolean nut
) {
}
