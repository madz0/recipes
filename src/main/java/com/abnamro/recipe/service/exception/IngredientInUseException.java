package com.abnamro.recipe.service.exception;

import java.util.UUID;

/**
 * Thrown when deleting an ingredient that is still referenced by one or more
 * recipes. Mapped to {@code 409 Conflict} by the web layer.
 */
public class IngredientInUseException extends RuntimeException {

    private final UUID id;

    public IngredientInUseException(UUID id, String name) {
        super("Ingredient '" + name + "' is still used by one or more recipes.");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
