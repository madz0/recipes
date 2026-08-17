package com.abnamro.recipe.model;

import java.util.Locale;
import java.util.Set;

/**
 * The dietary dimensions a {@link Recipe} is classified by — the single source of
 * truth shared by profile derivation ({@code RecipeService.deriveProfile}), the
 * {@code dietProfiles} search filter, and the {@link DietaryProfile} JSON.
 *
 * <p>Each flag is defined purely by an ingredient-type rule: a set of relevant
 * {@link IngredientType}s and a {@link Polarity} saying whether the flag is
 * {@code true} when those types are {@code PRESENT} in the recipe or {@code ABSENT}
 * from it. Deriving the profile and building the search SQL both read this one
 * definition, so the two can never drift apart, and adding a flag is a single edit.
 *
 * <p>Diet subset relationships fall out of the rules automatically: {@code VEGAN}
 * excludes a superset of {@code VEGETARIAN}'s types ({@code MEAT} plus
 * {@code DAIRY}/{@code EGG}), so every vegan recipe is also vegetarian and a
 * {@code vegetarian} search naturally includes vegan recipes.
 */
public enum DietaryFlag {

    VEGETARIAN(Set.of(IngredientType.MEAT), Polarity.ABSENT),
    VEGAN(Set.of(IngredientType.MEAT, IngredientType.DAIRY, IngredientType.EGG), Polarity.ABSENT),
    MEAT(Set.of(IngredientType.MEAT), Polarity.PRESENT),
    GLUTEN(Set.of(IngredientType.WHEAT), Polarity.PRESENT),
    WHEAT(Set.of(IngredientType.WHEAT, IngredientType.GLUTEN_FREE_WHEAT), Polarity.PRESENT),
    NUT(Set.of(IngredientType.NUT), Polarity.PRESENT);

    /** Whether the flag is {@code true} when its ingredient types are present or absent. */
    public enum Polarity {
        PRESENT,
        ABSENT
    }

    private final Set<IngredientType> types;
    private final Polarity polarity;
    private final String token;

    DietaryFlag(Set<IngredientType> types, Polarity polarity) {
        this.types = types;
        this.polarity = polarity;
        this.token = name().toLowerCase(Locale.ROOT);
    }

    /** The lowercase wire token for this flag — its JSON key and {@code dietProfiles} value. */
    public String token() {
        return token;
    }

    /** Resolves a wire token back to its flag, or {@code null} if unknown. */
    public static DietaryFlag fromToken(String token) {
        if (token != null) {
            for (DietaryFlag flag : values()) {
                if (flag.token.equals(token.toLowerCase(Locale.ROOT))) {
                    return flag;
                }
            }
        }
        return null;
    }

    /** {@code true} when this flag holds for a recipe using exactly the given ingredient types. */
    public boolean evaluate(Set<IngredientType> present) {
        boolean anyPresent = present.stream().anyMatch(types::contains);
        return (polarity == Polarity.PRESENT) == anyPresent;
    }
}
