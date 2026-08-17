package com.abnamro.recipe.service.exception;

import java.util.UUID;

/**
 * Thrown when no recipe exists for a given public UUID. Mapped to
 * {@code 404 Not Found} by the web layer.
 */
public class RecipeNotFoundException extends RuntimeException {

    private final UUID id;

    public RecipeNotFoundException(UUID id) {
        super("No recipe found with id " + id + ".");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
