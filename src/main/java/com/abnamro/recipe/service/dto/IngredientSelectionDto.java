package com.abnamro.recipe.service.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.abnamro.recipe.model.MeasurementUnit;

/** One ingredient chosen for a recipe, referencing an existing catalog ingredient by its public id. */
public record IngredientSelectionDto(UUID ingredientId, BigDecimal quantity, MeasurementUnit unit) {
}
