package com.abnamro.recipe.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.abnamro.recipe.model.DietaryFlag;
import com.abnamro.recipe.service.InvalidDietProfileException;

/** Unit tests for {@link RecipeMapper#toDietaryFilters} token parsing. */
@DisplayName("RecipeMapper.toDietaryFilters — dietProfiles token parsing")
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
}
