package com.abnamro.recipe.model;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Aggregate root representing a recipe. Holds its own name (there is no separate
 * dish resource), a serving count, free-text instructions, a JSON
 * {@link DietaryProfile} derived from its ingredients, and the set of ingredients
 * it uses.
 *
 * <p>{@code id} is the internal BIGINT primary key and is never exposed over the
 * API. {@code publicId} is the opaque, application-assigned UUID surrogate the API
 * uses to identify a recipe (mapped to the {@code public_id} column).
 */
@Table("recipe")
public record Recipe(
        @Id Long id,
        UUID publicId,
        String name,
        int servings,
        String instructions,
        @Column("dietary_profile_attributes") DietaryProfile dietaryProfile,
        @MappedCollection(idColumn = "recipe_id") Set<RecipeIngredient> ingredients
) {
}
