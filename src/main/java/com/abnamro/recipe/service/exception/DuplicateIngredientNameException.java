package com.abnamro.recipe.service.exception;

/**
 * Thrown when creating an ingredient whose name already exists. Ingredient names
 * are unique across the catalog. Mapped to {@code 409 Conflict} by the web layer.
 */
public class DuplicateIngredientNameException extends RuntimeException {

    private final String name;

    public DuplicateIngredientNameException(String name) {
        super("An ingredient named '" + name + "' already exists.");
        this.name = name;
    }

    public String ingredientName() {
        return name;
    }
}
