package com.abnamro.recipe.service.exception;

import java.util.UUID;

/**
 * Thrown when no ingredient exists for a given public UUID. Mapped to
 * {@code 404 Not Found} by the web layer.
 */
public class IngredientNotFoundException extends RuntimeException {

    private final UUID id;

    public IngredientNotFoundException(UUID id) {
        super("No ingredient found with id " + id + ".");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
