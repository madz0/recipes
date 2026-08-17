package com.abnamro.recipe.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.abnamro.recipe.model.DietaryFlag;
import com.abnamro.recipe.service.InvalidDietProfileException;

/** Unit tests for {@link RecipeMapper} query-token parsing (dietProfiles and ingredients). */
@DisplayName("RecipeMapper — dietProfiles & ingredients token parsing")
class RecipeMapperTest {

    @DisplayName("Bare token requires true, '-' token requires false")
    @Test
    void parsesSignsIntoRequiredValues() {
        Map<DietaryFlag, Boolean> filters = RecipeMapper.toDietaryFilters(List.of("vegan", "-gluten"));

        assertThat(filters).containsEntry(DietaryFlag.VEGAN, true)
                .containsEntry(DietaryFlag.GLUTEN, false)
                .hasSize(2);
    }

    @DisplayName("A single comma-separated value is split into multiple tokens")
    @Test
    void splitsCommaSeparatedValue() {
        Map<DietaryFlag, Boolean> filters = RecipeMapper.toDietaryFilters(List.of("vegetarian,-nut"));

        assertThat(filters).containsEntry(DietaryFlag.VEGETARIAN, true)
                .containsEntry(DietaryFlag.NUT, false)
                .hasSize(2);
    }

    @DisplayName("The same flag with both signs cancels out (no restriction)")
    @Test
    void bothSignsCancelOut() {
        Map<DietaryFlag, Boolean> filters = RecipeMapper.toDietaryFilters(List.of("gluten", "-gluten"));

        assertThat(filters).doesNotContainKey(DietaryFlag.GLUTEN).isEmpty();
    }

    @DisplayName("A cancelled flag stays cancelled even if requested again")
    @Test
    void cancelledFlagStaysCancelled() {
        Map<DietaryFlag, Boolean> filters =
                RecipeMapper.toDietaryFilters(List.of("gluten", "-gluten", "gluten"));

        assertThat(filters).doesNotContainKey(DietaryFlag.GLUTEN);
    }

    @DisplayName("Null or empty input yields no filters")
    @Test
    void nullOrEmptyYieldsNoFilters() {
        assertThat(RecipeMapper.toDietaryFilters(null)).isEmpty();
        assertThat(RecipeMapper.toDietaryFilters(List.of())).isEmpty();
    }

    @DisplayName("An unknown token is rejected with InvalidDietProfileException")
    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> RecipeMapper.toDietaryFilters(List.of("bogus")))
                .isInstanceOf(InvalidDietProfileException.class);
    }

    // --- toIngredientFilters (same form as dietProfiles) ------------------

    @DisplayName("ingredients: bare name → include, '-' name → exclude")
    @Test
    void ingredientsParsesSignsIntoIncludeExclude() {
        RecipeMapper.IngredientFilters filters =
                RecipeMapper.toIngredientFilters(List.of("Potatoes", "-Salmon"));

        assertThat(filters.include()).containsExactly("potatoes");
        assertThat(filters.exclude()).containsExactly("salmon");
    }

    @DisplayName("ingredients: a single comma-separated value is split into multiple tokens")
    @Test
    void ingredientsSplitsCommaSeparatedValue() {
        RecipeMapper.IngredientFilters filters =
                RecipeMapper.toIngredientFilters(List.of("Potatoes,Carrot,-Salmon"));

        assertThat(filters.include()).containsExactlyInAnyOrder("potatoes", "carrot");
        assertThat(filters.exclude()).containsExactly("salmon");
    }

    @DisplayName("ingredients: the same name with both signs cancels out (case-insensitive)")
    @Test
    void ingredientsBothSignsCancelOut() {
        RecipeMapper.IngredientFilters filters =
                RecipeMapper.toIngredientFilters(List.of("Egg", "-egg"));

        assertThat(filters.include()).isEmpty();
        assertThat(filters.exclude()).isEmpty();
    }

    @DisplayName("ingredients: a cancelled name stays cancelled even if requested again")
    @Test
    void ingredientsCancelledNameStaysCancelled() {
        RecipeMapper.IngredientFilters filters =
                RecipeMapper.toIngredientFilters(List.of("Egg,-Egg,Egg"));

        assertThat(filters.include()).isEmpty();
        assertThat(filters.exclude()).isEmpty();
    }

    @DisplayName("ingredients: a repeated name (same sign) is de-duplicated")
    @Test
    void ingredientsDeduplicatesRepeatedName() {
        RecipeMapper.IngredientFilters filters =
                RecipeMapper.toIngredientFilters(List.of("Potatoes,potatoes"));

        assertThat(filters.include()).containsExactly("potatoes");
        assertThat(filters.exclude()).isEmpty();
    }

    @DisplayName("ingredients: null or empty input yields empty include/exclude")
    @Test
    void ingredientsNullOrEmptyYieldsNoFilters() {
        RecipeMapper.IngredientFilters fromNull = RecipeMapper.toIngredientFilters(null);
        assertThat(fromNull.include()).isEmpty();
        assertThat(fromNull.exclude()).isEmpty();

        RecipeMapper.IngredientFilters fromEmpty = RecipeMapper.toIngredientFilters(List.of());
        assertThat(fromEmpty.include()).isEmpty();
        assertThat(fromEmpty.exclude()).isEmpty();
    }
}
