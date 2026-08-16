package com.abnamro.recipe.bootstrap;

import com.abnamro.recipe.model.IngredientType;

/**
 * One row parsed from {@code ingredients.json}. Maps to an
 * {@link com.abnamro.recipe.model.Ingredient} without an id — the id is assigned
 * by the database on insert.
 */
public record IngredientSeed(
        String name,
        IngredientType type
) {
}
