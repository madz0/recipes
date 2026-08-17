package com.abnamro.recipe.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.abnamro.recipe.model.Recipe;

/**
 * Spring Data JDBC repository for the {@link Recipe} aggregate. Filtered,
 * paginated listing is handled separately by {@link RecipeSearchRepository},
 * which needs dynamic SQL beyond what derived query methods can express.
 */
public interface RecipeRepository
        extends CrudRepository<Recipe, Long>, PagingAndSortingRepository<Recipe, Long> {

    /** Looks up a recipe by its public UUID surrogate (the API identity). */
    Optional<Recipe> findByPublicId(UUID publicId);
}
