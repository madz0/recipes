package com.abnamro.recipe.service.exception;

import java.util.UUID;

/**
 * Thrown when a recipe references an ingredient id that does not exist in the
 * shared catalog. Per the Recipes API contract this is a client error, so the web
 * layer maps it to {@code 400 Bad Request} (not 404).
 */
public class RecipeIngredientNotFoundException extends RuntimeException {

    private final UUID ingredientId;

    public RecipeIngredientNotFoundException(UUID ingredientId) {
        super("No ingredient found with id " + ingredientId + ".");
        this.ingredientId = ingredientId;
    }

    public UUID ingredientId() {
        return ingredientId;
    }
}
