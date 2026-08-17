package com.abnamro.recipe.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

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

    /**
     * How many recipe-ingredient join rows currently reference the given catalog
     * ingredient (internal id). Used to refuse deleting an in-use ingredient.
     */
    @Query("SELECT COUNT(*) FROM recipe_ingredient WHERE ingredient = :ingredientId")
    long countUsagesOfIngredient(@Param("ingredientId") Long ingredientId);
}
