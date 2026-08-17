package com.abnamro.recipe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for the enum-keyed {@link DietaryProfile}: derivation from ingredient
 * types (including the vegan/vegetarian split enabled by {@code DAIRY}/{@code EGG}),
 * the stable flat JSON wire format, and a guard that keeps {@link DietaryFlag} in
 * lockstep with the API contract.
 */
@DisplayName("DietaryProfile — derivation, JSON, and flag-coverage guard")
class DietaryProfileTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @DisplayName("A vegetable-only recipe is vegetarian and vegan with no allergens")
    @Test
    void vegetableOnlyIsVegetarianAndVegan() {
        DietaryProfile p = DietaryProfile.from(EnumSet.of(IngredientType.VEGETABLE));

        assertThat(p.is(DietaryFlag.VEGETARIAN)).isTrue();
        assertThat(p.is(DietaryFlag.VEGAN)).isTrue();
        assertThat(p.is(DietaryFlag.MEAT)).isFalse();
        assertThat(p.is(DietaryFlag.GLUTEN)).isFalse();
        assertThat(p.is(DietaryFlag.WHEAT)).isFalse();
        assertThat(p.is(DietaryFlag.NUT)).isFalse();
    }

    @DisplayName("Dairy or egg makes a recipe vegetarian but NOT vegan")
    @Test
    void dairyOrEggIsVegetarianButNotVegan() {
        DietaryProfile dairy = DietaryProfile.from(EnumSet.of(IngredientType.DAIRY));
        assertThat(dairy.is(DietaryFlag.VEGETARIAN)).isTrue();
        assertThat(dairy.is(DietaryFlag.VEGAN)).isFalse();
        assertThat(dairy.is(DietaryFlag.MEAT)).isFalse();

        DietaryProfile egg = DietaryProfile.from(EnumSet.of(IngredientType.EGG));
        assertThat(egg.is(DietaryFlag.VEGETARIAN)).isTrue();
        assertThat(egg.is(DietaryFlag.VEGAN)).isFalse();
    }

    @DisplayName("Meat makes a recipe neither vegetarian nor vegan")
    @Test
    void meatIsNeitherVegetarianNorVegan() {
        DietaryProfile p = DietaryProfile.from(EnumSet.of(IngredientType.MEAT));

        assertThat(p.is(DietaryFlag.MEAT)).isTrue();
        assertThat(p.is(DietaryFlag.VEGETARIAN)).isFalse();
        assertThat(p.is(DietaryFlag.VEGAN)).isFalse();
    }

    @DisplayName("Gluten comes from WHEAT only; wheat also from GLUTEN_FREE_WHEAT; nut from NUT")
    @Test
    void allergenFlagsFollowIngredientTypes() {
        DietaryProfile wheat = DietaryProfile.from(EnumSet.of(IngredientType.WHEAT));
        assertThat(wheat.is(DietaryFlag.WHEAT)).isTrue();
        assertThat(wheat.is(DietaryFlag.GLUTEN)).isTrue();

        DietaryProfile glutenFree = DietaryProfile.from(EnumSet.of(IngredientType.GLUTEN_FREE_WHEAT));
        assertThat(glutenFree.is(DietaryFlag.WHEAT)).isTrue();
        assertThat(glutenFree.is(DietaryFlag.GLUTEN)).isFalse();

        DietaryProfile nut = DietaryProfile.from(EnumSet.of(IngredientType.NUT));
        assertThat(nut.is(DietaryFlag.NUT)).isTrue();
    }

    @DisplayName("JSON round-trips to an equal profile and keeps the flat six-boolean shape")
    @Test
    void jsonRoundTripKeepsFlatShape() {
        DietaryProfile original =
                DietaryProfile.from(EnumSet.of(IngredientType.DAIRY, IngredientType.WHEAT));

        String json = mapper.writeValueAsString(original);

        // Flat object with exactly one key per flag, values reflecting the profile.
        assertThat(json).isEqualTo(
                "{\"vegetarian\":true,\"vegan\":false,\"meat\":false,"
                        + "\"gluten\":true,\"wheat\":true,\"nut\":false}");

        DietaryProfile roundTripped = mapper.readValue(json, DietaryProfile.class);
        assertThat(roundTripped).isEqualTo(original);
    }

    /**
     * Guard: the set of dietary flags must exactly match the boolean properties of the
     * generated API {@code DietaryProfile}. Since derivation and search both iterate
     * {@link DietaryFlag#values()}, this test protects the one hand-mapped boundary —
     * the OpenAPI contract / DTO — so a flag can't be added (or removed) on one side
     * without the other.
     */
    @DisplayName("DietaryFlag tokens stay in lockstep with the API DietaryProfile fields")
    @Test
    void flagsMatchApiContract() {
        Set<String> flagTokens = Arrays.stream(DietaryFlag.values())
                .map(DietaryFlag::token)
                .collect(Collectors.toSet());

        Set<String> dtoBooleanProperties = Arrays.stream(
                        com.abnamro.recipe.api.model.DietaryProfile.class.getMethods())
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> m.getName().startsWith("get"))
                .filter(DietaryProfileTest::returnsBoolean)
                .map(m -> m.getName().substring(3).toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        assertThat(dtoBooleanProperties).isEqualTo(flagTokens);
    }

    private static boolean returnsBoolean(Method m) {
        return m.getReturnType() == Boolean.class || m.getReturnType() == boolean.class;
    }
}
