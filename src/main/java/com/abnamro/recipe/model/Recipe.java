package com.abnamro.recipe.model;

import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Aggregate root representing a recipe for a {@link Dish}. Holds its serving
 * count, free-text instructions, a JSON {@link DietaryProfile}, a reference to
 * the owning dish, and the set of ingredients it uses.
 */
@Table("recipe")
public record Recipe(
        @Id Long id,
        int servings,
        String instructions,
        @Column("dietary_profile_attributes") DietaryProfile dietaryProfile,
        @Column("dish") AggregateReference<Dish, Long> dish,
        @MappedCollection(idColumn = "recipe_id") Set<RecipeIngredient> ingredients
) {
}
