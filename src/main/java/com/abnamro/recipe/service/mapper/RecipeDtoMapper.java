package com.abnamro.recipe.service.mapper;

import java.util.Map;

import org.mapstruct.Context;
import org.mapstruct.Mapper;

import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.Recipe;
import com.abnamro.recipe.model.RecipeIngredient;
import com.abnamro.recipe.service.dto.RecipeDto;

/**
 * Maps a persisted {@link Recipe} to the self-contained {@link RecipeDto} read model. Scalar
 * fields (including the domain {@code DietaryProfile}, identical on both sides) map by name;
 * each ingredient line is joined against the catalog lookup passed as {@link Context}, so the
 * mapper needs no repository access of its own.
 */
@Mapper(componentModel = "spring")
public interface RecipeDtoMapper {

    /** A persisted recipe plus the resolved catalog lookup → the flattened {@link RecipeDto}. */
    RecipeDto toDto(Recipe recipe, @Context Map<Long, Ingredient> byId);

    /** Joins one stored {@link RecipeIngredient} (FK + quantity + unit) with its catalog details. */
    default RecipeDto.Ingredient toIngredientDto(RecipeIngredient ri, @Context Map<Long, Ingredient> byId) {
        Ingredient ing = byId.get(ri.ingredient().getId());
        return new RecipeDto.Ingredient(ing.publicId(), ing.name(), ri.quantity(), ri.unit());
    }
}
