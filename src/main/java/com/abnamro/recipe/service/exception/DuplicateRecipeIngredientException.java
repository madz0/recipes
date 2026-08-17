package com.abnamro.recipe.service.exception;

import java.util.UUID;

/**
 * Thrown when a recipe create/update selection lists the same catalog ingredient
 * more than once. The join table's composite primary key is {@code (recipe_id,
 * ingredient)}, so duplicates are a client error mapped to {@code 400 Bad Request}.
 */
public class DuplicateRecipeIngredientException extends RuntimeException {

    private final UUID ingredientId;

    public DuplicateRecipeIngredientException(UUID ingredientId) {
        super("Ingredient id " + ingredientId + " is selected more than once.");
        this.ingredientId = ingredientId;
    }

    public UUID ingredientId() {
        return ingredientId;
    }
}
