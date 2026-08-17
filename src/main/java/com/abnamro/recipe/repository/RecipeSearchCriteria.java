package com.abnamro.recipe.repository;

import java.util.List;
import java.util.Map;

import com.abnamro.recipe.model.DietaryFlag;

/**
 * Optional, AND-combined filters for listing recipes. A {@code null}/empty field
 * means "no restriction on this dimension".
 *
 * @param dietaryFilters       required value ({@code true}/{@code false}) per
 *                             {@link DietaryFlag}; an empty map means no dietary
 *                             restriction. Built from the {@code dietProfiles}
 *                             query parameter.
 * @param servings             exact serving count, or {@code null}
 * @param includeIngredients   recipe must contain ALL of these ingredient names
 *                             (case-insensitive, exact)
 * @param excludeIngredients   recipe must contain NONE of these ingredient names
 *                             (case-insensitive, exact)
 * @param instructionsContains full-text search term matched against the instructions
 */
public record RecipeSearchCriteria(
        Map<DietaryFlag, Boolean> dietaryFilters,
        Integer servings,
        List<String> includeIngredients,
        List<String> excludeIngredients,
        String instructionsContains
) {
}
