package com.abnamro.recipe.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.IngredientType;

/**
 * Spring Data JDBC repository for the shared {@link Ingredient} catalog.
 */
public interface IngredientRepository
        extends CrudRepository<Ingredient, Long>, PagingAndSortingRepository<Ingredient, Long> {

    /**
     * Loads the ingredients whose names are in the given set. Used by the
     * bootstrap runner to check, one batch at a time, which seed rows already
     * exist — a single query per batch instead of one lookup per ingredient.
     */
    List<Ingredient> findByNameIn(Collection<String> names);

    /** Looks up an ingredient by its public UUID surrogate (the API identity). */
    Optional<Ingredient> findByPublicId(UUID publicId);

    /**
     * Loads every ingredient whose public id is in the given set, in a single
     * query. Used when creating a recipe to resolve all selected ingredients at
     * once instead of one lookup per selection.
     */
    List<Ingredient> findByPublicIdIn(Collection<UUID> publicIds);

    /** A page of ingredients restricted to a single type, for the {@code type} filter. */
    Page<Ingredient> findByType(IngredientType type, Pageable pageable);

    /** Whether an ingredient with the given (unique) name already exists. */
    boolean existsByName(String name);
}
