package com.abnamro.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.test.context.ActiveProfiles;

import com.abnamro.recipe.model.DietaryFlag;
import com.abnamro.recipe.model.DietaryProfile;
import com.abnamro.recipe.model.Ingredient;
import com.abnamro.recipe.model.IngredientType;
import com.abnamro.recipe.model.MeasurementUnit;
import com.abnamro.recipe.model.Recipe;
import com.abnamro.recipe.model.RecipeIngredient;
import com.abnamro.recipe.service.IngredientService;

/**
 * Repository-level test for the dietary filter of {@link RecipeSearchRepository}.
 *
 * <p>Complements the end-to-end {@code RecipeApiIT} by exercising the search repository
 * directly (returning internal recipe ids) on H2, which drives the {@code dietary_profile_attributes}
 * {@code LIKE} branch. It proves dietary filtering is satisfied purely from the denormalized
 * profile column — no ingredient join — including the {@code flag == false} case, and that the
 * stored profile round-trips correctly through the JSON converters. Recipes are persisted with
 * a profile derived exactly as {@code RecipeService.create} does, so the column matches production.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RecipeSearchRepository — dietary filtering via dietary_profile_attributes")
class RecipeSearchRepositoryTest {

    private static final Pageable PAGE = PageRequest.of(0, 100, Sort.by("name"));

    @Autowired
    private RecipeSearchRepository search;

    @Autowired
    private com.abnamro.recipe.repository.RecipeRepository recipes;

    @Autowired
    private IngredientService ingredientService;

    /** Persists a single-ingredient recipe with a profile derived from that ingredient's type. */
    private Long persistRecipe(IngredientType type) {
        Ingredient ingredient = ingredientService.create("search-it " + UUID.randomUUID(), type);
        DietaryProfile profile = DietaryProfile.from(EnumSet.of(type));
        Recipe saved = recipes.save(new Recipe(
                null, UUID.randomUUID(), "search-it " + UUID.randomUUID(), 2, "cook it", profile,
                Set.of(new RecipeIngredient(
                        AggregateReference.to(ingredient.id()), BigDecimal.ONE, MeasurementUnit.GRAMS))));
        return saved.id();
    }

    private java.util.List<Long> searchIds(Map<DietaryFlag, Boolean> dietary) {
        return search.search(new RecipeSearchCriteria(dietary, null, null, null, null), PAGE).ids();
    }

    @DisplayName("vegetarian=true matches a vegetable recipe and excludes a meat recipe")
    @Test
    void vegetarianTrueMatchesVegetableViaColumn() {
        Long vegetable = persistRecipe(IngredientType.VEGETABLE);
        Long meat = persistRecipe(IngredientType.MEAT);

        assertThat(searchIds(Map.of(DietaryFlag.VEGETARIAN, true)))
                .contains(vegetable)
                .doesNotContain(meat);
    }

    @DisplayName("meat=false matches a non-vegan (dairy) recipe and excludes a meat recipe")
    @Test
    void meatFalseMatchesNonMeatRegardlessOfOtherFlags() {
        Long dairy = persistRecipe(IngredientType.DAIRY); // vegetarian=true, vegan=false, meat=false
        Long meat = persistRecipe(IngredientType.MEAT);

        assertThat(searchIds(Map.of(DietaryFlag.MEAT, false)))
                .contains(dairy)
                .doesNotContain(meat);
    }

    @DisplayName("meat=true matches a meat recipe and excludes a vegetable recipe")
    @Test
    void meatTrueExcludesNonMeat() {
        Long meat = persistRecipe(IngredientType.MEAT);
        Long vegetable = persistRecipe(IngredientType.VEGETABLE);

        assertThat(searchIds(Map.of(DietaryFlag.MEAT, true)))
                .contains(meat)
                .doesNotContain(vegetable);
    }
}
