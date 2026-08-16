package com.abnamro.recipe.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Aggregate root representing a reusable ingredient in the shared catalog.
 * Referenced from recipes via {@link RecipeIngredient}.
 *
 * <p>{@code id} is the internal BIGINT primary key and is never exposed over the
 * API. {@code publicId} is the opaque, application-assigned UUID surrogate the API
 * uses to identify an ingredient (mapped to the {@code public_id} column).
 */
@Table("ingredient")
public record Ingredient(
        @Id Long id,
        UUID publicId,
        String name,
        IngredientType type
) {
}
