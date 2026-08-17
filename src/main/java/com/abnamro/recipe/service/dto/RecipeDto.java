package com.abnamro.recipe.service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.abnamro.recipe.model.DietaryProfile;
import com.abnamro.recipe.model.MeasurementUnit;

/**
 * Self-contained read model for a recipe as returned by the API: the recipe's
 * public id and scalar fields, its derived {@link DietaryProfile}, and its
 * ingredients pre-joined with catalog identity/name. Holds no persistence
 * aggregate, so the web mapper needs neither id lookups nor domain types.
 */
public record RecipeDto(
        UUID publicId,
        String name,
        int servings,
        String instructions,
        DietaryProfile dietaryProfile,
        List<Ingredient> ingredients) {

    /** One ingredient line of a recipe: catalog identity + name, plus the per-recipe amount. */
    public record Ingredient(UUID publicId, String name, BigDecimal quantity, MeasurementUnit unit) {
    }
}
