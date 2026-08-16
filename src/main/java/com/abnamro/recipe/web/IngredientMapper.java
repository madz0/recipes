package com.abnamro.recipe.web;

import java.util.List;

import org.springframework.data.domain.Page;

import com.abnamro.recipe.api.model.Ingredient;
import com.abnamro.recipe.api.model.IngredientPage;
import com.abnamro.recipe.api.model.IngredientType;

/**
 * Maps between the persistence-layer {@link com.abnamro.recipe.model.Ingredient}
 * aggregate and the OpenAPI-generated DTOs. The domain types are referenced by
 * fully-qualified name because their simple names clash with the generated DTOs.
 */
final class IngredientMapper {

    private IngredientMapper() {
    }

    /** Domain ingredient → API DTO. Exposes {@code publicId} as the API {@code id}. */
    static Ingredient toDto(com.abnamro.recipe.model.Ingredient entity) {
        return new Ingredient(entity.publicId(), entity.name(), toDtoType(entity.type()));
    }

    /** A page of domain ingredients → the API {@code IngredientPage}. */
    static IngredientPage toPageDto(Page<com.abnamro.recipe.model.Ingredient> page) {
        List<Ingredient> content = page.getContent().stream()
                .map(IngredientMapper::toDto)
                .toList();
        return new IngredientPage(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** API enum → domain enum (constant names are identical). Null-safe for filters. */
    static com.abnamro.recipe.model.IngredientType toDomainType(IngredientType type) {
        return type == null ? null : com.abnamro.recipe.model.IngredientType.valueOf(type.name());
    }

    private static IngredientType toDtoType(com.abnamro.recipe.model.IngredientType type) {
        return IngredientType.valueOf(type.name());
    }
}
